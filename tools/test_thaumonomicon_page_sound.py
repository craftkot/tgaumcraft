#!/usr/bin/env python3
"""BETA26 Thaumonomicon page-sound contract, compiling the real item with Java stubs.

This proves local-vs-server use behavior and exact resource bytes, NOT audible
playback or a full NeoForge client run.
"""
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ITEM = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/ThaumonomiconItem.java"
REGISTRY = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java"
ASSETS = ROOT / "src/main/resources/assets/thaumcraft"
EXPECTED_BLOBS = {
    "page1.ogg": "a0c4f5b01b58e5720e79978637c20f425b79f2c5",
    "page2.ogg": "2db59662cb448028f88c54d8df245be03d00b09e",
}
for filename, expected in EXPECTED_BLOBS.items():
    data = (ASSETS / "sounds" / filename).read_bytes()
    assert data.startswith(b"OggS"), filename + " must be original Ogg Vorbis"
    git_sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\\0" + data).hexdigest()
    assert git_sha == expected, (filename, git_sha, expected)

sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
assert sounds["page"] == {
    "category": "master",
    "sounds": ["thaumcraft:page1", "thaumcraft:page2"],
}, "BETA26 page must randomly choose the two original clips"
assert "scan" in sounds, "existing Thaumometer scan event must remain registered"
registry = REGISTRY.read_text(encoding="utf-8")
assert 'SOUND_EVENTS.register("page"' in registry
assert 'ThaumcraftPort.MOD_ID, "page"' in registry

STUBS = {
"net/minecraft/world/InteractionHand.java": """
package net.minecraft.world;
public enum InteractionHand { MAIN_HAND }
""",
"net/minecraft/world/InteractionResultHolder.java": """
package net.minecraft.world;
public final class InteractionResultHolder<T> {
  public final T object;
  private InteractionResultHolder(T value) { object=value; }
  public static <T> InteractionResultHolder<T> success(T value) {
    return new InteractionResultHolder<>(value);
  }
}
""",
"net/minecraft/sounds/SoundSource.java": """
package net.minecraft.sounds;
public enum SoundSource { PLAYERS }
""",
"net/minecraft/world/item/ItemStack.java": """
package net.minecraft.world.item;
public final class ItemStack {}
""",
"net/minecraft/world/item/Item.java": """
package net.minecraft.world.item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class Item {
  public static final class Properties {}
  public Item(Properties properties) {}
  public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
    return InteractionResultHolder.success(player.getItemInHand(hand));
  }
}
""",
"net/minecraft/world/entity/player/Player.java": """
package net.minecraft.world.entity.player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
public class Player {
  public final ItemStack held = new ItemStack();
  public ItemStack getItemInHand(InteractionHand hand) { return held; }
  public double getX() { return 12.5; }
  public double getY() { return 64.0; }
  public double getZ() { return -7.5; }
}
""",
"net/minecraft/world/level/Level.java": """
package net.minecraft.world.level;
import net.minecraft.sounds.SoundSource;
public class Level {
  private final boolean client;
  public int plays;
  public double x,y,z;
  public Object sound;
  public SoundSource category;
  public float volume,pitch;
  public boolean distanceDelay;
  public Level(boolean client) { this.client=client; }
  public boolean isClientSide() { return client; }
  public void playLocalSound(double x,double y,double z,Object sound,SoundSource category,
                             float volume,float pitch,boolean distanceDelay) {
    plays++;this.x=x;this.y=y;this.z=z;this.sound=sound;this.category=category;
    this.volume=volume;this.pitch=pitch;this.distanceDelay=distanceDelay;
  }
}
""",
"dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java": """
package dev.ehtid.thaumcraft.registry;
import java.util.function.Supplier;
public final class ThaumcraftSounds {
  public static final Object PAGE_EVENT = new Object();
  public static final Supplier<Object> PAGE = () -> PAGE_EVENT;
}
""",
"dev/ehtid/thaumcraft/client/FirstStepsBookScreen.java": """
package dev.ehtid.thaumcraft.client;
public final class FirstStepsBookScreen {
  public static int opens;
  public static void open() { opens++; }
}
""",
"dev/ehtid/thaumcraft/item/ThaumonomiconPageSoundTest.java": """
package dev.ehtid.thaumcraft.item;
import dev.ehtid.thaumcraft.client.FirstStepsBookScreen;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
public final class ThaumonomiconPageSoundTest {
  private static int checks;
  private static void check(boolean ok,String message) {
    checks++; if(!ok)throw new AssertionError(message);
  }
  public static void main(String[] args) {
    ThaumonomiconItem item = new ThaumonomiconItem(new Item.Properties());
    Player player = new Player();
    Level client = new Level(true);
    var result = item.use(client,player,InteractionHand.MAIN_HAND);
    check(result.object==player.held,"use must return held stack");
    check(client.plays==1,"client use must play exactly once");
    check(client.sound==ThaumcraftSounds.PAGE_EVENT,"must play original page event");
    check(client.category==SoundSource.PLAYERS,"source category");
    check(client.x==12.5 && client.y==64 && client.z==-7.5,"player coordinates");
    check(client.volume==1.0f && client.pitch==1.0f,"BETA26 volume and pitch");
    check(!client.distanceDelay,"no distance delay");
    check(FirstStepsBookScreen.opens==1,"client must still open book");
    Level server = new Level(false);
    var serverResult = item.use(server,player,InteractionHand.MAIN_HAND);
    check(serverResult.object==player.held,"server returns held stack");
    check(server.plays==0,"server must not double-play local sound");
    check(FirstStepsBookScreen.opens==1,"server must not open client GUI");
    System.out.println("ThaumonomiconPageSoundTest PASS: "+checks+" assertions");
  }
}
""",
}
with tempfile.TemporaryDirectory(prefix="tc-book-page-") as tmp:
    root = Path(tmp)
    sources = [str(ITEM)]
    for relative, contents in STUBS.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
        sources.append(str(path))
    classes = root / "classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "21", "-d", str(classes), *sources], check=True)
    subprocess.run(["java", "-cp", str(classes),
                    "dev.ehtid.thaumcraft.item.ThaumonomiconPageSoundTest"], check=True)
print("Thaumonomicon original page1/page2 OGG and sound registry: PASS")
