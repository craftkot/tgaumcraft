# M42 — отдельные JVM сервера и клиента, настоящий TCP

20.10.2026, Java21, Minecraft1.21.1/NeoForge21.1.250, Xvfb1280×800/Mesa llvmpipe. Dedicated server слушает только127.0.0.1:25616; изолированный offline creative fixture с одним Dev player. Это реальное TCP-соединение двух JVM, без integrated ServerLevel в клиенте; внешняя интернет-сессия/второй одновременный клиент не проверялись.

Снимки необработанные, прямо из окна:

- [areas.png](areas.png): реальный initial snapshot трёх печатей после входа на сервер.
- [wall_normal.png](wall_normal.png): bell в offhand, стена закрывает контуры.
- [wall_sneak.png](wall_sneak.png): те же контуры видны через стену при реальном Shift; actual GL flag проверен после explicit depth disable.
- [edit.png](edit.png): реальные X+/color+ C2S, область4/1/2, color5; клиент получил update с прежним instance UUID.
- [removed.png](removed.png): реальный single seal drop после удержанного Shift+ПКМ, удалённый Pickup contour, две остальные печати сохранены.
- [assertions.txt](assertions.txt): выбранные реальные строки исходных логов, явно UTC. Полные логи: `/workspace/reference/golems-seal-remote-depth-{server,client}.log`.

Server BUILD SUCCESS3m24s, actual TCP login20:20:44UTC; client remote/snapshot20:20:47, GL depth disabled previous=true20:21:09, menu update20:21:32, exact-one-drop/REMOVE20:22:04. Сервер автоматически останавливается через200ticks после завершения fixture; все измерения сохранены20:22:24UTC. Клиент затем штатно закрыт через список серверов→главное меню→Выйти из игры; точный exit status зафиксирован в lane/validation.

Первый отдельный run обнаружил NO_DEPTH_TEST no-op: callbacks были, raw through-wall screenshot оказался неверным. Он сохранён внеGit и не выдаётся за успешный визуальный proof. `fd38183` явно отключает глубину в scoped state и восстанавливает предыдущий GL flag. Более ранние config boolean/string overload и manual-client missing Minecraft classpath ошибки произошли до игровых assertions и сохранены в отдельных логах.

Воспроизведение из подготовленного checkout/cache:

1. Свежий `build/golem-seal-server`, eula/server.properties с локальным адресом выше, offline mode, creative, flat, view/simulation2. Не перезаписывать старый fixture world: три исходные координаты должны быть свободны. Обычные запуски эти фикстуры не активируют.
2. `JAVA_HOME` указывает Java21. Выполнить `gradle --offline --init-script tools/golems.gradle -PgolemClientScenario=seals -PgolemRemoteAddress=127.0.0.1:25616 build -x test prepareGolemClientRun runGolemSealServer`.
3. Пока сервер работает, запускать клиента из подготовленных ModDev args напрямую: два Gradle JavaExec в одном checkout могут ждать project lock. Следующий Python запускается из корня репозитория с подготовленным DISPLAY/Mesa environment; он повторяет проверенный запуск без печати classpath или окружения:

```python
from pathlib import Path
import os, subprocess
root=Path.cwd(); dev=root/'build/moddev'
mc=list((dev/'artifacts').glob('neoforge-*.jar'))
mc=[p for p in mc if p.stem.removeprefix('neoforge-').replace('.','').isdigit()]
assert len(mc)==1
cp=[str(root/'build/classes/java/main'),str(root/'build/resources/main'),str(mc[0]),
    *(dev/'golemClientLegacyClasspath.txt').read_text().splitlines()]
client_env=os.environ.copy()
client_env.pop('THAUMCRAFT_SMOKE',None)
client_env['MOD_CLASSES']=os.pathsep.join('thaumcraft%%'+str(root/p)
    for p in ['build/classes/java/main','build/resources/main'])
subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-cp',os.pathsep.join(cp),
    '@'+str(dev/'golemClientRunVmArgs.txt'),'@'+str(dev/'golemClientRunProgramArgs.txt')],
    cwd=root/'build/golem-client',env=client_env,check=True)
```

4. Дождаться REMOTE_CONNECTION_OBSERVED/SNAPSHOT_ASSERTIONS_OK, затем F, normal screenshot, удержать Shift/sneak screenshot, отпустить Shift и F. В открытом owner menu нажать X+ и color+ по одному разу. Дождаться server edit/client update, Escape, BELL_REMOVE_READY, удержать Shift до серверного crouch tick и ПКМ по верхней грани Pickup. Нужны оба server/client REMOVE markers. Через200ticks сервер штатно завершится; клиент закрыть через GUI.

Нативные233/233 и Core39962/2901 проверены отдельно; последующие production изменения только клиентская глубина. Исходные bitmap/grid/модели/анимации/particles не портированы: текущие линии provisional. Один TCP client не заменяет multi-user stress/packet-loss/crash-injection matrix.
