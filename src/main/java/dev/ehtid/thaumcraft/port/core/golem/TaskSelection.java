package dev.ehtid.thaumcraft.port.core.golem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Source priority/distance rules, with world lookup and seal policy supplied by the adapter. */
public final class TaskSelection {
    private TaskSelection() {}

    public record Position(double x, double y, double z) {}

    /** canPerform is the seal/AI's required+forbidden trait, owner, home and capacity gate. */
    public record SealRule(byte color, Predicate<GolemTask> canPerform) {
        public SealRule { Objects.requireNonNull(canPerform, "canPerform"); }
    }

    /**
     * targetPosition returns the entity's current floored block coordinates, without
     * a .5 offset; empty means absent/dead. Block targets do not need world lookup.
     * An empty sealRule follows Task.canGolemPerformTask: no seal permits the task.
     */
    public record Context(UUID golemId, byte color, Position position,
                          Function<GolemTask, Optional<Position>> targetPosition,
                          Function<GolemTask, Optional<SealRule>> sealRule) {
        public Context {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(targetPosition, "targetPosition");
            Objects.requireNonNull(sealRule, "sealRule");
        }
    }

    private record Candidate(GolemTask task, double score) {}

    public static double score(Position targetBlock, Position golem, byte priority) {
        double dx = targetBlock.x + 0.5 - golem.x;
        double dy = targetBlock.y + 0.5 - golem.y;
        double dz = targetBlock.z + 0.5 - golem.z;
        return dx * dx + dy * dy + dz * dz - 256 * priority;
    }

    public static boolean colorMatches(byte golemColor, byte sealColor) {
        return golemColor <= 0 || sealColor <= 0 || golemColor == sealColor;
    }

    /**
     * Like the source, UUID affinity applies only when both UUIDs are nonnull.
     * Unlike its raw sorting helper, this public candidate list excludes already
     * suspended/completed/expired work, which its AI/cleanup otherwise had to reject.
     */
    public static List<GolemTask> sorted(Iterable<GolemTask> tasks,
                                         GolemTask.Type type, Context context) {
        List<Candidate> candidates = new ArrayList<>();
        for (GolemTask task : tasks) {
            if (task.getType() != type || task.isReserved() || task.isSuspended()
                    || task.isCompleted() || task.getLifespan() <= 0) continue;
            if (context.golemId != null && task.getGolemUUID() != null
                    && !context.golemId.equals(task.getGolemUUID())) continue;

            Position target;
            if (type == GolemTask.Type.BLOCK) {
                SealArea.BlockPos pos = task.getPos();
                target = new Position(pos.x(), pos.y(), pos.z());
            } else {
                Optional<Position> resolved = context.targetPosition.apply(task);
                if (resolved.isEmpty()) {
                    task.setSuspended(true);
                    continue;
                }
                target = resolved.get();
            }
            Optional<SealRule> seal = context.sealRule.apply(task);
            if (seal.isPresent() && (!colorMatches(context.color, seal.get().color)
                    || !seal.get().canPerform.test(task))) continue;
            candidates.add(new Candidate(task, score(target, context.position, task.getPriority())));
        }
        // List.sort is stable. Source uses insertion order for equal score; no new priority rule.
        candidates.sort(Comparator.comparingDouble(Candidate::score));
        return candidates.stream().map(Candidate::task).toList();
    }
}
