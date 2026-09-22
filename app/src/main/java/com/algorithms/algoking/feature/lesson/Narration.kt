package com.algorithms.algoking.feature.lesson

import com.algorithms.algoking.engine.narration.NarrationId
import com.algorithms.algoking.engine.narration.NarrationKey

/**
 * Resolves engine narration keys to copy — ARCHITECTURE.md §5.4.
 *
 * The engine never holds a string. This is the only place algorithm vocabulary
 * turns into English, which is what keeps `:engine` a pure JVM module and the app
 * translatable without touching algorithm code.
 */
object Narration {

    fun resolve(key: NarrationKey?): String {
        key ?: return ""
        val a = key.args
        fun arg(i: Int): String = a.getOrNull(i)?.toString() ?: ""

        /** A counted argument, for copy that has to agree with itself about number. */
        fun count(i: Int): Int = (a.getOrNull(i) as? Int) ?: 0
        fun plural(i: Int, one: String, many: String): String =
            if (count(i) == 1) one else many

        return when (key.id) {

            // ── Two Pointers ──────────────────────────────────────────────
            // Textbook vocabulary throughout: LEFT, RIGHT, sum, target, pair.
            // The learner will meet these words in every write-up of the
            // technique, so the app uses them rather than a friendlier synonym.
            // One word each, the way Binary Search and SWAP/KEEP are
            // (DESIGN_SYSTEM.md §6.9). Three buttons share one row, which leaves
            // about 93dp per label: "Move RIGHT" wrapped to three lines and
            // overflowed the 56dp button, and even "← RIGHT" broke at the space
            // while "LEFT →" did not — so the row read as ragged.
            //
            // The direction lives where it teaches instead of where it wraps: the
            // pointer labels under the array, the prompt, and the feedback line
            // ("24 was over 17, so RIGHT moves left to a smaller value").
            NarrationId.TP_OPTION_MOVE_LEFT -> "LEFT"
            NarrationId.TP_OPTION_MOVE_RIGHT -> "RIGHT"
            NarrationId.TP_OPTION_FOUND -> "FOUND"
            NarrationId.TP_ASK_WHICH_POINTER ->
                "The sum is ${arg(0)} and the target is ${arg(1)}. What now?"

            NarrationId.TP_SUM_IS -> "${arg(0)} + ${arg(1)} = ${arg(2)}"
            NarrationId.TP_SUM_LESS -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.TP_SUM_GREATER -> "${arg(0)} is greater than ${arg(1)}."
            NarrationId.TP_SUM_EQUAL -> "${arg(0)} equals ${arg(1)}."
            NarrationId.TP_MOVED_LEFT -> "LEFT now points at ${arg(0)}."
            NarrationId.TP_MOVED_RIGHT -> "RIGHT now points at ${arg(1)}."
            NarrationId.TP_WINDOW_CLOSED -> "The pointers have met. No pair adds up."
            NarrationId.TP_FOUND -> "${arg(0)} + ${arg(1)} = ${arg(2)}. Pair found."

            NarrationId.TP_HINT_COMPARE ->
                "LEFT is ${arg(0)}, RIGHT is ${arg(1)}. They add to ${arg(2)}, " +
                    "and you need ${arg(3)}."

            NarrationId.TP_RETRY_LOOK_AGAIN ->
                "Look at the sum again: ${arg(0)} against a target of ${arg(1)}."

            // Rung 2 asks the question rather than answering it. "Do we need a
            // larger sum or a smaller one?" is the thought that turns the rule
            // into understanding, and it is one step short of the answer.
            NarrationId.TP_RETRY_ASK_LARGER ->
                "The sum is too small. Which pointer can give you a larger value?"

            NarrationId.TP_RETRY_ASK_SMALLER ->
                "The sum is too big. Which pointer can give you a smaller value?"

            NarrationId.TP_RETRY_ASK_EQUAL ->
                "The sum already matches the target. Is there anything left to move?"

            NarrationId.TP_RETRY_EXPLAIN_LEFT ->
                "${arg(0)} is smaller than ${arg(1)}, so you need a bigger sum. " +
                    "The array is sorted, so only LEFT can move to a larger value. " +
                    "Move LEFT right."

            NarrationId.TP_RETRY_EXPLAIN_RIGHT ->
                "${arg(0)} is greater than ${arg(1)}, so you need a smaller sum. " +
                    "The array is sorted, so only RIGHT can move to a smaller value. " +
                    "Move RIGHT left."

            NarrationId.TP_RETRY_EXPLAIN_FOUND ->
                "${arg(0)} is exactly ${arg(1)}. This is the pair — take it."

            NarrationId.TP_WHY_LEFT_WRONG ->
                "Moving LEFT right makes the sum larger, and ${arg(0)} is already " +
                    "above ${arg(1)}."

            NarrationId.TP_WHY_RIGHT_WRONG ->
                "Moving RIGHT left makes the sum smaller, and ${arg(0)} is already " +
                    "below ${arg(1)}."

            NarrationId.TP_WHY_NOT_FOUND_YET ->
                "${arg(0)} is not ${arg(1)} yet, so this is not the pair."

            NarrationId.TP_WHY_MOVE_PAST_PAIR ->
                "Moving either pointer now would step straight past the answer."

            NarrationId.TP_CORRECT_LEFT ->
                "${arg(2)} was short of ${arg(3)}, so LEFT moves right to a larger value."

            NarrationId.TP_CORRECT_RIGHT ->
                "${arg(2)} was over ${arg(3)}, so RIGHT moves left to a smaller value."

            NarrationId.TP_CORRECT_FOUND -> "${arg(0)} + ${arg(1)} = ${arg(3)}. That is the pair."

            // ── Two Pointers — WATCH ──────────────────────────────────────
            NarrationId.TP_WATCH_SETUP -> "Find two values that add up to ${arg(0)}."
            NarrationId.TP_WATCH_SETUP_SUPPORT ->
                "The array is sorted. That is the only thing this technique needs."

            NarrationId.TP_WATCH_ENDS -> "Start at both ends."
            NarrationId.TP_WATCH_ENDS_SUPPORT ->
                "LEFT at index ${arg(0)}, RIGHT at index ${arg(1)}."

            NarrationId.TP_WATCH_SUM -> "${arg(0)} + ${arg(1)} = ${arg(2)}"
            NarrationId.TP_WATCH_SUM_LESS -> "${arg(0)} is smaller than the target, ${arg(1)}."
            NarrationId.TP_WATCH_SUM_GREATER -> "${arg(0)} is greater than the target, ${arg(1)}."
            NarrationId.TP_WATCH_SUM_EQUAL -> "${arg(0)} is exactly the target."

            NarrationId.TP_WATCH_MOVE_LEFT -> "Move LEFT one position right."
            // The *reason*, and it is the whole technique: the value that just
            // left took every pair it belonged to with it.
            NarrationId.TP_WATCH_MOVE_LEFT_WHY ->
                "${arg(0)} is the smallest value left, so no pair using it can reach " +
                    "the target. All of them go at once."

            NarrationId.TP_WATCH_MOVE_RIGHT -> "Move RIGHT one position left."
            NarrationId.TP_WATCH_MOVE_RIGHT_WHY ->
                "${arg(0)} is the largest value left, so every pair using it overshoots. " +
                    "All of them go at once."

            NarrationId.TP_WATCH_NO_PAIR -> "No two values add up to ${arg(0)}."
            NarrationId.TP_WATCH_WINDOW_CLOSED ->
                "The pointers met. Every pair has been ruled out."

            NarrationId.TP_WATCH_FOUND -> "Pair found."
            NarrationId.TP_WATCH_FOUND_SUPPORT -> "${arg(0)} + ${arg(1)} = ${arg(2)}."

            NarrationId.TP_WATCH_INSIGHT -> "Each move rules out a whole row of pairs."
            NarrationId.TP_WATCH_INSIGHT_SUPPORT ->
                "${arg(0)} values make dozens of pairs. This found the answer in " +
                    "${arg(1)} comparisons."

            NarrationId.TP_WATCH_SUMMARY_FOUND ->
                "Found a pair summing to ${arg(0)} in ${arg(1)} comparisons."

            NarrationId.TP_WATCH_SUMMARY_NO_PAIR ->
                "Proved no pair sums to ${arg(0)}, in ${arg(1)} comparisons."

            NarrationId.TP_WATCH_SUMMARY_SUPPORT -> "Now run it yourself."
            NarrationId.TP_IDEA_1 -> "Start LEFT at the first value and RIGHT at the last."
            NarrationId.TP_IDEA_2 -> "Sum too big? Move RIGHT left, to a smaller value."
            NarrationId.TP_IDEA_3 -> "Sum too small? Move LEFT right, to a larger value."
            NarrationId.TP_IDEA_4 -> "It works because the array is sorted — and only then."


            // ── Prefix Sum ────────────────────────────────────────────────
            // One representation throughout: prefix is n+1 long, prefix[0] = 0,
            // prefix[i+1] = prefix[i] + array[i], and a range is
            // prefix[right+1] - prefix[left]. The copy never shows the other form.
            NarrationId.PS_OPTION_VALUE -> arg(0)
            NarrationId.PS_OPTION_INDICES -> "p[${arg(0)}] − p[${arg(1)}]"
            NarrationId.PS_ASK_NEXT_PREFIX -> "What is prefix[${arg(0)}]?"
            NarrationId.PS_BUILT -> "prefix[${arg(0)}] = ${arg(1)} + ${arg(2)} = ${arg(3)}"

            NarrationId.PS_HINT_BUILD ->
                "prefix[${arg(0)}] = prefix[${arg(1)}] + array[${arg(2)}]."

            NarrationId.PS_RETRY_BUILD_LOOK ->
                "Not quite. prefix[${arg(0)}] builds on the value before it."

            NarrationId.PS_RETRY_BUILD_ASK ->
                "You have a running total of ${arg(0)} and the next value is ${arg(1)}. " +
                    "What do they make?"

            NarrationId.PS_RETRY_BUILD_EXPLAIN ->
                "prefix[${arg(0)}] uses the previous prefix sum plus the current value: " +
                    "${arg(1)} + ${arg(2)} = ${arg(3)}."

            // Named for the misconception, not for the number.
            NarrationId.PS_WHY_FORGOT_RUNNING_TOTAL ->
                "That is only the current value. A prefix sum carries everything before " +
                    "it too — add it to ${arg(0)}."

            NarrationId.PS_WHY_FORGOT_TO_ADD ->
                "That is the previous prefix sum unchanged. You still have to add ${arg(1)}."

            NarrationId.PS_WHY_WRONG_OPERANDS ->
                "That adds two array values together. A prefix sum adds one array value to " +
                    "the previous *prefix*: ${arg(0)} + ${arg(1)} = ${arg(2)}."

            NarrationId.PS_CORRECT_BUILD ->
                "${arg(0)} + ${arg(1)} = ${arg(2)}. prefix[${arg(3)}] now holds everything " +
                    "up to that point."

            NarrationId.PS_ASK_WHICH_INDICES ->
                "Which two prefix values give the sum from ${arg(0)} to ${arg(1)}?"

            NarrationId.PS_ASK_EVALUATE ->
                "prefix[${arg(0)}] − prefix[${arg(1)}] = ${arg(2)} − ${arg(3)}. What is it?"

            NarrationId.PS_CHOSE_INDICES -> "prefix[${arg(0)}] − prefix[${arg(1)}]"
            NarrationId.PS_ANSWERED -> "Sum of ${arg(0)}..${arg(1)} is ${arg(2)}."

            NarrationId.PS_HINT_FORMULA ->
                "rangeSum(left, right) = prefix[right + 1] − prefix[left]."

            NarrationId.PS_HINT_SUBTRACT -> "Work out ${arg(0)} − ${arg(1)}."

            NarrationId.PS_RETRY_INDICES_LOOK ->
                "Not quite. Check which prefix value sits just past the end of the range."

            NarrationId.PS_RETRY_INDICES_ASK ->
                "prefix[${arg(0)}] stops *before* index ${arg(0)}. Which index includes it?"

            NarrationId.PS_RETRY_INDICES_EXPLAIN ->
                "The range is ${arg(0)} to ${arg(1)}, so take prefix[${arg(2)}] — everything " +
                    "up to and including ${arg(1)} — and subtract prefix[${arg(3)}], " +
                    "everything before the range."

            NarrationId.PS_WHY_STOPS_SHORT ->
                "prefix[${arg(0)}] stops one short: it leaves out array[${arg(3)}], the last " +
                    "value in the range."

            NarrationId.PS_WHY_KEEPS_TOO_MUCH ->
                "Subtracting prefix[${arg(1)}] leaves array[${arg(1)}] in, and it is before " +
                    "the range starts."

            NarrationId.PS_WHY_DROPS_TOO_MUCH ->
                "Subtracting prefix[${arg(1)}] removes part of the range itself."

            NarrationId.PS_CORRECT_INDICES ->
                "prefix[${arg(0)}] − prefix[${arg(1)}]. Everything up to the end of the " +
                    "range, minus everything before it."

            NarrationId.PS_RETRY_EVAL_LOOK -> "Check the subtraction: ${arg(0)} − ${arg(1)}."
            NarrationId.PS_RETRY_EVAL_ASK -> "Subtract, do not add — you are removing a prefix."
            NarrationId.PS_RETRY_EVAL_EXPLAIN -> "${arg(0)} − ${arg(1)} = ${arg(2)}."

            NarrationId.PS_CORRECT_ANSWER ->
                "${arg(0)} − ${arg(1)} = ${arg(2)}. That is the sum of ${arg(3)}..${arg(4)}, " +
                    "in one subtraction."

            // ── Prefix Sum — WATCH ────────────────────────────────────────
            NarrationId.PS_WATCH_SETUP -> "Add up any range, without adding it up."
            NarrationId.PS_WATCH_SETUP_SUPPORT ->
                "First we build a table of running totals. Then every range costs one " +
                    "subtraction."

            NarrationId.PS_WATCH_SEED -> "The prefix array starts with 0."
            NarrationId.PS_WATCH_SEED_SUPPORT ->
                "It is one cell longer than the array, and that leading 0 means \"nothing " +
                    "yet\" — which is what makes the range formula work with no exceptions."

            NarrationId.PS_WATCH_BUILD -> "${arg(0)} + ${arg(1)} = ${arg(2)}"
            NarrationId.PS_WATCH_BUILD_SUPPORT ->
                "prefix[${arg(0)}] = prefix[${arg(1)}] + array[${arg(2)}]."

            NarrationId.PS_WATCH_BUILD_DONE ->
                "The table is built. Every cell holds the sum of everything before it."

            NarrationId.PS_WATCH_QUERY -> "Now: the sum from index ${arg(0)} to ${arg(1)}."
            NarrationId.PS_WATCH_QUERY_INDICES ->
                "Take prefix[${arg(0)}] — everything up to and including ${arg(2)} — and " +
                    "subtract prefix[${arg(1)}], everything before ${arg(3)}."

            NarrationId.PS_WATCH_ANSWER -> "${arg(0)} − ${arg(1)} = ${arg(2)}"
            NarrationId.PS_WATCH_ANSWER_WHY ->
                "The prefix before the range is removed, leaving exactly " +
                    "array[${arg(0)}..${arg(1)}]."

            NarrationId.PS_WATCH_INSIGHT -> "One subtraction, however long the range."
            NarrationId.PS_WATCH_INSIGHT_SUPPORT ->
                "Building the table costs O(n) once. After that every range query is O(1) — " +
                    "the same single step whether the range covers 2 values or all ${arg(0)}."

            NarrationId.PS_WATCH_SUMMARY -> "That is Prefix Sum."
            NarrationId.PS_WATCH_SUMMARY_SUPPORT -> "Now build one yourself."
            NarrationId.PS_IDEA_1 -> "prefix[0] = 0, and prefix is one longer than the array."
            NarrationId.PS_IDEA_2 -> "prefix[i + 1] = prefix[i] + array[i]."
            NarrationId.PS_IDEA_3 -> "rangeSum(left, right) = prefix[right + 1] − prefix[left]."
            NarrationId.PS_IDEA_4 -> "Build once in O(n); answer every query in O(1)."


            // ── Graph DFS ─────────────────────────────────────────────────
            // Standard vocabulary throughout: node, edge, neighbour, visited,
            // current node, backtrack, traversal.
            NarrationId.DFS_OPTION_NODE -> arg(0)
            NarrationId.DFS_ASK_NEXT -> "You are at ${arg(0)}. Where does DFS go next?"
            NarrationId.DFS_ASK_DEAD_END ->
                "${arg(0)} has no unvisited neighbours. Where does DFS go now?"

            NarrationId.DFS_VISITED -> "Visited ${arg(0)}."
            NarrationId.DFS_BACKTRACKED -> "Backtracked from ${arg(0)} to ${arg(1)}."

            NarrationId.DFS_HINT_DEEPER ->
                "Look at ${arg(0)}'s neighbours in order, and take the first one DFS " +
                    "has not visited."

            NarrationId.DFS_HINT_BACKTRACK ->
                "There is nowhere deeper to go from ${arg(0)}. DFS returns the way it came."

            NarrationId.DFS_RETRY_LOOK -> "Not quite. Check ${arg(0)}'s neighbours again."
            NarrationId.DFS_RETRY_ASK ->
                "Which of ${arg(0)}'s neighbours has DFS not visited yet — and which comes first?"

            NarrationId.DFS_RETRY_EXPLAIN ->
                "DFS goes as deep as it can, through the first unvisited neighbour. " +
                    "From ${arg(0)} that is ${arg(1)}."

            NarrationId.DFS_RETRY_DEAD_END_LOOK ->
                "Not quite. Every neighbour of ${arg(0)} has already been visited."

            NarrationId.DFS_RETRY_DEAD_END_ASK ->
                "${arg(0)} is a dead end. Where did DFS come from?"

            NarrationId.DFS_RETRY_DEAD_END_EXPLAIN ->
                "${arg(0)} has no unvisited neighbours, so DFS backtracks to ${arg(1)} — " +
                    "the node it came from — and looks for another branch there."

            // Each wrong tap is the misconception it encodes, named.
            NarrationId.DFS_WHY_ALREADY_VISITED ->
                "${arg(0)} is already visited. DFS skips visited neighbours — that is what " +
                    "stops it going round in circles."

            NarrationId.DFS_WHY_WRONG_BRANCH ->
                "${arg(0)} is a neighbour, but not the first unvisited one. DFS finishes the " +
                    "${arg(1)} branch completely before it starts another."

            NarrationId.DFS_WHY_TOO_EARLY ->
                "Not yet — ${arg(2)} still has an unvisited neighbour. DFS only backtracks " +
                    "from a dead end."

            NarrationId.DFS_CORRECT_DEEPER ->
                "${arg(1)} is ${arg(0)}'s first unvisited neighbour, so DFS goes deeper."

            NarrationId.DFS_CORRECT_BACKTRACK ->
                "${arg(0)} was a dead end, so DFS backtracks to ${arg(1)} and looks for " +
                    "another branch."

            // ── Graph DFS — WATCH ─────────────────────────────────────────
            NarrationId.DFS_WATCH_SETUP -> "Depth-first search, starting at ${arg(0)}."
            NarrationId.DFS_WATCH_SETUP_SUPPORT ->
                "Go as deep as possible down one path. Hit a dead end, back up, take the " +
                    "next one."

            NarrationId.DFS_WATCH_VISIT_START -> "Visit ${arg(0)}."
            NarrationId.DFS_WATCH_VISIT_START_SUPPORT ->
                "Mark it visited, and remember how we got here."

            NarrationId.DFS_WATCH_GO_DEEPER -> "${arg(0)} → ${arg(1)}. Go deeper."
            NarrationId.DFS_WATCH_FIRST_UNVISITED ->
                "DFS takes the first neighbour it has not visited."

            NarrationId.DFS_WATCH_SKIPPED ->
                "${arg(0)} is already visited, so DFS skips it and takes ${arg(1)}."

            NarrationId.DFS_WATCH_SKIPPED_MANY ->
                "${arg(0)} are already visited, so DFS skips them and takes ${arg(1)}."

            NarrationId.DFS_WATCH_DEAD_END -> "${arg(0)} has no unvisited neighbours."
            NarrationId.DFS_WATCH_BACKTRACK ->
                "Dead end. DFS backtracks from ${arg(0)} to ${arg(1)} and looks for another " +
                    "branch there."

            NarrationId.DFS_WATCH_COMPLETE -> "Every node reached. Traversal: ${arg(0)}."
            NarrationId.DFS_WATCH_INSIGHT -> "Deep first, wide last."
            NarrationId.DFS_WATCH_INSIGHT_SUPPORT ->
                "DFS finished the whole B branch — D and E — before it ever looked at C. " +
                    "Backtracking is what lets it come back for C at all."

            NarrationId.DFS_WATCH_SUMMARY -> "Traversal: ${arg(0)}."
            NarrationId.DFS_WATCH_SUMMARY_SUPPORT -> "Now run it yourself."
            NarrationId.DFS_IDEA_1 -> "Visit a node, then go deeper into its first unvisited neighbour."
            NarrationId.DFS_IDEA_2 -> "Skip neighbours already visited — that is what prevents cycles."
            NarrationId.DFS_IDEA_3 -> "At a dead end, backtrack to the node you came from."
            NarrationId.DFS_IDEA_4 -> "Recursion does the remembering; the call stack is the path."


            // ── Graph BFS ─────────────────────────────────────────────────
            // Standard vocabulary: node, edge, neighbour, visited, current,
            // queue, enqueue, dequeue, traversal, level.
            NarrationId.BFS_OPTION_NODE -> arg(0)
            NarrationId.BFS_ASK_ENQUEUE ->
                "${arg(0)} is out of the queue. Which neighbour joins the queue next?"

            NarrationId.BFS_ASK_DEQUEUE -> "Nothing left to add. Which node comes off the queue?"
            NarrationId.BFS_SEEDED -> "${arg(0)} is visited and in the queue."
            NarrationId.BFS_ENQUEUED -> "Enqueued ${arg(0)}."
            NarrationId.BFS_DEQUEUED -> "Dequeued ${arg(0)}."

            NarrationId.BFS_HINT_ENQUEUE ->
                "Go through ${arg(0)}'s neighbours in order and add the first one BFS has " +
                    "not seen."

            NarrationId.BFS_HINT_DEQUEUE ->
                "A queue is first in, first out. ${arg(1)} has been waiting longest."

            NarrationId.BFS_RETRY_ENQUEUE_LOOK ->
                "Not quite. Check ${arg(0)}'s neighbours again — which has BFS not seen?"

            NarrationId.BFS_RETRY_ENQUEUE_ASK ->
                "${arg(0)} still has a neighbour to add. Which one, and in what order?"

            NarrationId.BFS_RETRY_ENQUEUE_EXPLAIN ->
                "BFS adds ${arg(0)}'s unseen neighbours in order, so ${arg(1)} goes to the " +
                    "back of the queue next."

            NarrationId.BFS_RETRY_DEQUEUE_LOOK ->
                "Not quite. ${arg(0)} has nothing left to add, so something comes off the queue."

            NarrationId.BFS_RETRY_DEQUEUE_ASK ->
                "Which node has been in the queue longest? That is the one BFS takes."

            NarrationId.BFS_RETRY_DEQUEUE_EXPLAIN ->
                "The queue is first in, first out, so ${arg(1)} comes off the front."

            // Each wrong tap named for the misconception it encodes.
            NarrationId.BFS_WHY_ALREADY_VISITED ->
                "${arg(0)} is already visited. BFS marks a node the moment it joins the " +
                    "queue, so it never gets added twice."

            NarrationId.BFS_WHY_NOT_THE_FRONT ->
                "${arg(0)} is in the queue, but ${arg(3)} is at the front. BFS processes " +
                    "them in the order they arrived."

            NarrationId.BFS_WHY_STILL_ADDING ->
                "${arg(0)} is already in the queue and will get its turn. ${arg(2)} still " +
                    "has a neighbour to add first."

            NarrationId.BFS_WHY_WRONG_ORDER ->
                "${arg(0)} is a neighbour BFS has not seen, but ${arg(1)} comes first in " +
                    "${arg(2)}'s list."

            NarrationId.BFS_CORRECT_ENQUEUE ->
                "${arg(0)} joins the back of the queue, and is marked visited straight away."

            NarrationId.BFS_CORRECT_DEQUEUE ->
                "${arg(0)} was at the front, so it comes off first."

            // ── Graph BFS — WATCH ─────────────────────────────────────────
            NarrationId.BFS_WATCH_SETUP -> "Breadth-first search, starting at ${arg(0)}."
            NarrationId.BFS_WATCH_SETUP_SUPPORT ->
                "Finish the whole current level before going deeper. A queue is what makes " +
                    "that happen."

            NarrationId.BFS_WATCH_SEED -> "Visit ${arg(0)} and put it in the queue."
            NarrationId.BFS_WATCH_SEED_SUPPORT ->
                "A node is marked visited when it *joins* the queue, not when it leaves — " +
                    "that is what stops it being queued twice."

            NarrationId.BFS_WATCH_ENQUEUE -> "${arg(1)} → ${arg(0)}. Enqueue it."
            NarrationId.BFS_WATCH_DEQUEUE -> "Dequeue ${arg(0)} from the front."
            NarrationId.BFS_WATCH_QUEUE_NOW -> "Queue: ${arg(0)}."
            NarrationId.BFS_WATCH_QUEUE_EMPTYING -> "The queue is empty."
            NarrationId.BFS_WATCH_SKIPPED ->
                "${arg(0)} is already visited, so BFS skips it and adds ${arg(1)}."

            NarrationId.BFS_WATCH_SKIPPED_MANY ->
                "${arg(0)} are already visited, so BFS skips them and adds ${arg(1)}."

            NarrationId.BFS_WATCH_COMPLETE ->
                "The queue is empty, so BFS is complete. Traversal: ${arg(0)}."

            NarrationId.BFS_WATCH_INSIGHT -> "Level by level, because the queue says so."
            NarrationId.BFS_WATCH_INSIGHT_SUPPORT ->
                "BFS gave ${arg(0)}. DFS on this same graph gives A → B → D → E → C. Same " +
                    "graph, same neighbour order — the queue is the only difference."

            NarrationId.BFS_WATCH_SUMMARY -> "Traversal: ${arg(0)}."
            NarrationId.BFS_WATCH_SUMMARY_SUPPORT -> "Now run it yourself."
            NarrationId.BFS_IDEA_1 -> "Mark a node visited when it joins the queue, not when it leaves."
            NarrationId.BFS_IDEA_2 -> "Take the front of the queue; add unseen neighbours to the back."
            NarrationId.BFS_IDEA_3 -> "First in, first out is what produces level-by-level order."
            NarrationId.BFS_IDEA_4 -> "Empty queue means every reachable node has been processed."

            // ── Binary Search Tree ────────────────────────────────────────
            // LEFT / RIGHT / FOUND, one word each, for the same reason Two
            // Pointers uses them: three buttons share one row, and a label that
            // wraps to three lines reads as ragged. The *direction* is taught in
            // the prompt and the feedback, where it has room to say why.
            NarrationId.BST_OPTION_LEFT -> "LEFT"
            NarrationId.BST_OPTION_RIGHT -> "RIGHT"
            NarrationId.BST_OPTION_FOUND -> "FOUND"
            NarrationId.BST_ASK_WHICH_WAY ->
                "The target is ${arg(0)} and this node is ${arg(1)}. Which way?"

            NarrationId.BST_COMPARED_LESS -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.BST_COMPARED_GREATER -> "${arg(0)} is greater than ${arg(1)}."
            NarrationId.BST_COMPARED_EQUAL -> "${arg(0)} matches this node."
            NarrationId.BST_MOVED_LEFT -> "Left from ${arg(0)} to ${arg(1)}."
            NarrationId.BST_MOVED_RIGHT -> "Right from ${arg(0)} to ${arg(1)}."
            NarrationId.BST_NO_LEFT_CHILD ->
                "${arg(0)} has no left child, so ${arg(2)} is not in this tree."

            NarrationId.BST_NO_RIGHT_CHILD ->
                "${arg(0)} has no right child, so ${arg(2)} is not in this tree."

            NarrationId.BST_FOUND -> "${arg(0)} found, in ${arg(1)} comparisons."
            NarrationId.BST_HINT_RULE ->
                "Compare ${arg(0)} with ${arg(1)}: smaller values are LEFT, larger are RIGHT."

            NarrationId.BST_RETRY_LOOK ->
                "Look at the comparison again: ${arg(0)} against ${arg(1)}."

            NarrationId.BST_RETRY_ASK_SMALLER ->
                "${arg(0)} is smaller than ${arg(1)}. Which side of a node holds the " +
                    "smaller values?"

            NarrationId.BST_RETRY_ASK_LARGER ->
                "${arg(0)} is greater than ${arg(1)}. Which side of a node holds the " +
                    "larger values?"

            NarrationId.BST_RETRY_ASK_EQUAL ->
                "You are standing on ${arg(0)}. What is there left to search?"

            NarrationId.BST_RETRY_EXPLAIN_LEFT ->
                "${arg(0)} is smaller than ${arg(1)}, and every value smaller than a node " +
                    "is in its left subtree. Go LEFT."

            NarrationId.BST_RETRY_EXPLAIN_RIGHT ->
                "${arg(0)} is greater than ${arg(1)}, and every value greater than a node " +
                    "is in its right subtree. Go RIGHT."

            NarrationId.BST_RETRY_EXPLAIN_FOUND ->
                "${arg(0)} and ${arg(1)} are the same value. This is the node — the search " +
                    "is over."

            // Each wrong option is answered with the invariant on the side the
            // learner reached for, so the feedback teaches the rule rather than
            // reporting a verdict.
            NarrationId.BST_WHY_LEFT_IMPOSSIBLE ->
                "Everything left of ${arg(1)} is smaller than ${arg(1)}, and ${arg(0)} is " +
                    "bigger. It cannot be down there."

            NarrationId.BST_WHY_RIGHT_IMPOSSIBLE ->
                "Everything right of ${arg(1)} is larger than ${arg(1)}, and ${arg(0)} is " +
                    "smaller. It cannot be down there."

            NarrationId.BST_WHY_ALREADY_HERE ->
                "You are already on ${arg(0)}. Moving would walk away from the answer."

            NarrationId.BST_WHY_NOT_THIS_NODE ->
                "This node is ${arg(1)}, and you are looking for ${arg(0)}."

            // What the right answer *achieved* — which subtree just left the
            // search — rather than the word "correct".
            NarrationId.BST_CORRECT_LEFT ->
                "${arg(0)} is smaller than ${arg(1)}, so everything to the right of " +
                    "${arg(1)} is out — ${arg(2)} ${plural(2, "node", "nodes")} gone in one " +
                    "comparison."

            NarrationId.BST_CORRECT_RIGHT ->
                "${arg(0)} is greater than ${arg(1)}, so everything to the left of " +
                    "${arg(1)} is out — ${arg(2)} ${plural(2, "node", "nodes")} gone in one " +
                    "comparison."

            NarrationId.BST_CORRECT_LEFT_EMPTY ->
                "${arg(0)} is smaller than ${arg(1)}, so the search goes left."

            NarrationId.BST_CORRECT_RIGHT_EMPTY ->
                "${arg(0)} is greater than ${arg(1)}, so the search goes right."

            NarrationId.BST_CORRECT_FOUND ->
                "${arg(0)} matches. ${arg(3)} comparisons, and the rest of the tree was " +
                    "never touched."

            // ── Binary Search Tree — WATCH ────────────────────────────────
            NarrationId.BST_WATCH_SETUP -> "Find ${arg(0)} in this binary search tree."
            NarrationId.BST_WATCH_SETUP_SUPPORT ->
                "Every value in a node's left subtree is smaller than it, and every value " +
                    "in its right subtree is larger."

            NarrationId.BST_WATCH_ROOT -> "Start at the root: ${arg(0)}."
            NarrationId.BST_WATCH_ROOT_SUPPORT ->
                "A BST search starts at the root and only ever walks downwards."

            NarrationId.BST_WATCH_COMPARE_LESS -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.BST_WATCH_COMPARE_GREATER -> "${arg(0)} is greater than ${arg(1)}."
            NarrationId.BST_WATCH_COMPARE_EQUAL -> "${arg(0)} matches the current node."
            NarrationId.BST_WATCH_RULE_LEFT ->
                "In a BST, values smaller than a node are in its LEFT subtree."

            NarrationId.BST_WATCH_RULE_RIGHT ->
                "In a BST, values greater than a node are in its RIGHT subtree."

            NarrationId.BST_WATCH_RULE_EQUAL -> "That is the search: the target is this node."
            NarrationId.BST_WATCH_MOVE_LEFT -> "Move LEFT."
            NarrationId.BST_WATCH_MOVE_RIGHT -> "Move RIGHT."
            // The values that leave are named, so "a whole subtree" is a sentence
            // about specific numbers rather than an abstraction.
            NarrationId.BST_WATCH_MOVE_WHY ->
                "${arg(0)} cannot be on that side of ${arg(1)}, so ${arg(2)} leave the search."

            NarrationId.BST_WATCH_MOVE_WHY_NONE ->
                "${arg(0)} had nothing on its other side to rule out."

            NarrationId.BST_WATCH_NOT_FOUND ->
                "${arg(1)} has no child that way. ${arg(0)} is not in this tree."

            NarrationId.BST_WATCH_NOT_FOUND_SUPPORT ->
                "Walking off the end of the tree is how a BST proves a value is absent."

            NarrationId.BST_WATCH_FOUND -> "Found ${arg(0)}."
            NarrationId.BST_WATCH_FOUND_SUPPORT ->
                "${arg(0)} comparisons, and ${arg(1)} of the ${arg(2)} nodes were never " +
                    "looked at."

            NarrationId.BST_WATCH_INSIGHT -> "One path from the root, not a scan of every node."
            // Both halves of the complexity claim, together. O(log n) is a
            // property of a *balanced* tree, and a lesson that says only the
            // happy half teaches something untrue.
            NarrationId.BST_WATCH_INSIGHT_SUPPORT ->
                "Each comparison ruled out a whole subtree: ${arg(0)} of ${arg(1)} nodes were " +
                    "never looked at. Balanced, that is O(log n) — but a skewed tree behaves " +
                    "like a linked list and costs O(n)."

            NarrationId.BST_WATCH_SUMMARY -> "Search path: ${arg(0)}."
            NarrationId.BST_WATCH_SUMMARY_NOT_FOUND ->
                "${arg(0)} is not in the tree, and ${arg(1)} proved it."

            NarrationId.BST_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.BST_IDEA_1 -> "Smaller than the node? Go LEFT."
            NarrationId.BST_IDEA_2 -> "Larger than the node? Go RIGHT."
            NarrationId.BST_IDEA_3 -> "Equal? That is the node you were looking for."
            NarrationId.BST_IDEA_4 -> "Every comparison rules out an entire subtree."
            NarrationId.BST_IDEA_5 ->
                "Binary Search halves a sorted array by arithmetic. A BST keeps that " +
                    "halving in its shape."

            // ── AVL Tree ──────────────────────────────────────────────────
            // The learner taps nodes, so the "options" are the values themselves.
            NarrationId.AVL_OPTION_NODE -> arg(0)
            NarrationId.AVL_ASK_PIVOT -> "Which node is out of balance?"
            NarrationId.AVL_ASK_RISER -> "Which node moves up to take ${arg(0)}'s place?"
            NarrationId.AVL_INSERTED_BALANCED ->
                "${arg(0)} goes below ${arg(1)}. Every node is still within ±1."

            NarrationId.AVL_INSERTED_BROKE_IT ->
                "${arg(0)} goes below ${arg(1)}, and that pushes a node past ±1."

            NarrationId.AVL_PIVOT_CHOSEN -> "${arg(0)} is out of balance at ${arg(1)}."
            NarrationId.AVL_RISER_SINGLE -> "${arg(0)} comes up into ${arg(1)}'s place."
            NarrationId.AVL_RISER_DOUBLE ->
                "${arg(0)} comes up into ${arg(1)}'s place — two rotations to get it there."

            NarrationId.AVL_ROTATED_LEFT -> "Left rotation at ${arg(0)}."
            NarrationId.AVL_ROTATED_RIGHT -> "Right rotation at ${arg(0)}."
            NarrationId.AVL_ROTATED_FIRST_HALF -> "First rotation, at ${arg(0)}."
            NarrationId.AVL_HINT_PIVOT ->
                "Read the balance factors. AVL allows −1, 0 and +1 — nothing else."

            NarrationId.AVL_HINT_RISER_STRAIGHT ->
                "The imbalance under ${arg(0)} runs the same way twice, so one rotation " +
                    "is enough."

            NarrationId.AVL_HINT_RISER_BENT ->
                "The imbalance under ${arg(0)} changes direction. A child cannot fix a " +
                    "zig-zag on its own."

            NarrationId.AVL_RETRY_PIVOT_LOOK -> "Look at the balance factors again."
            NarrationId.AVL_RETRY_PIVOT_ASK ->
                "Which node has a factor outside −1 to +1 — and if more than one does, " +
                    "which is the lowest?"

            NarrationId.AVL_RETRY_PIVOT_EXPLAIN ->
                "${arg(0)} is at ${arg(2)}, which is outside the ±1 AVL allows. That is " +
                    "the node to rotate."

            NarrationId.AVL_RETRY_RISER_LOOK ->
                "Follow the two steps down from ${arg(0)} toward the new value."

            NarrationId.AVL_RETRY_RISER_ASK_STRAIGHT ->
                "Both steps go the same way. Which node is directly below ${arg(0)} on " +
                    "that side?"

            NarrationId.AVL_RETRY_RISER_ASK_BENT ->
                "The two steps go opposite ways. Which node sits at the bottom of that bend?"

            NarrationId.AVL_RETRY_RISER_EXPLAIN_STRAIGHT ->
                "The path below ${arg(0)} runs straight, so its child ${arg(1)} comes up " +
                    "and ${arg(0)} goes down to the other side. One rotation."

            NarrationId.AVL_RETRY_RISER_EXPLAIN_BENT ->
                "The path below ${arg(0)} bends, so the grandchild ${arg(1)} is the one " +
                    "that comes up — ${arg(2)} and ${arg(0)} end up either side of it."

            // Each wrong tap is answered with the rule, never with a verdict.
            NarrationId.AVL_WHY_STILL_BALANCED ->
                "${arg(0)} is at ${arg(1)}, which AVL allows. Only ±2 needs a rotation."

            NarrationId.AVL_WHY_NOT_LOWEST ->
                "${arg(0)} is out of balance too, but ${arg(2)} is lower. Fix the lowest " +
                    "one and the ones above it come back on their own."

            NarrationId.AVL_WHY_PIVOT_ITSELF ->
                "${arg(0)} is the node that has to move down. Something below it takes " +
                    "its place."

            NarrationId.AVL_WHY_OUTSIDE ->
                "${arg(0)} is not below ${arg(1)}. A rotation only rearranges the nodes " +
                    "under the one that is out of balance."

            // The single most common AVL mistake: treating a bent path like a
            // straight one and bringing the child up.
            NarrationId.AVL_WHY_CHILD_NOT_ENOUGH ->
                "Bring ${arg(0)} up and the path still bends the same way — ${arg(1)} " +
                    "would be out of balance all over again. The grandchild is the one " +
                    "that straightens it."

            NarrationId.AVL_WHY_NOT_ON_THE_PATH ->
                "${arg(0)} is below ${arg(1)}, but it is not on the path the new value " +
                    "took. It is not what made the tree lopsided."

            NarrationId.AVL_CORRECT_PIVOT ->
                "${arg(0)} is at ${arg(1)} — the first node the new value pushed past ±1."

            NarrationId.AVL_CORRECT_RISER_STRAIGHT ->
                "${arg(0)} comes up, ${arg(1)} goes down the other side, and the subtree " +
                    "is level again. One rotation."

            NarrationId.AVL_CORRECT_RISER_BENT ->
                "The path bends, so ${arg(0)} — the grandchild — is the one that comes up. " +
                    "It takes two rotations to get it there."

            // ── AVL Tree — WATCH ──────────────────────────────────────────
            NarrationId.AVL_WATCH_SETUP -> "Insert ${arg(0)} into this AVL tree."
            NarrationId.AVL_WATCH_SETUP_SUPPORT ->
                "Each node shows its balance factor: the height of its left side minus " +
                    "its right. AVL allows −1, 0 and +1."

            NarrationId.AVL_WATCH_INSERT -> "Insert ${arg(0)}."
            NarrationId.AVL_WATCH_STILL_BALANCED ->
                "Every balance factor is still within ±1, so there is nothing to fix. " +
                    "Not every insert needs a rotation."

            NarrationId.AVL_WATCH_BROKE_IT ->
                "${arg(0)} is now at ${arg(1)}. The tree has to be repaired."

            NarrationId.AVL_WATCH_PIVOT -> "${arg(0)} is out of balance at ${arg(1)}."
            NarrationId.AVL_WATCH_SHAPE_STRAIGHT ->
                "The two steps below ${arg(0)} go the same way — the imbalance runs straight."

            NarrationId.AVL_WATCH_SHAPE_BENT ->
                "The two steps below ${arg(0)} go opposite ways — the imbalance bends."

            NarrationId.AVL_WATCH_RISER -> "${arg(0)} moves up into ${arg(1)}'s place."
            NarrationId.AVL_WATCH_RISER_SINGLE ->
                "The path runs straight, so the child comes up. One rotation."

            NarrationId.AVL_WATCH_RISER_DOUBLE ->
                "The path bends, so the grandchild comes up. That takes two rotations."

            NarrationId.AVL_WATCH_ROTATE_LEFT -> "Left rotation at ${arg(0)}."
            NarrationId.AVL_WATCH_ROTATE_RIGHT -> "Right rotation at ${arg(0)}."
            NarrationId.AVL_WATCH_ROTATE_FIRST -> "First rotation, at ${arg(0)}."
            NarrationId.AVL_WATCH_ROTATE_FIRST_WHY ->
                "This one does not fix anything yet. It straightens the bend so the " +
                    "second rotation can be an ordinary single."

            NarrationId.AVL_WATCH_ROTATE_DONE ->
                "Every factor is back within ±1, and the tree is ${arg(0)} levels tall."

            NarrationId.AVL_WATCH_INSIGHT -> "A rotation changes depth, never order."
            NarrationId.AVL_WATCH_INSIGHT_SUPPORT ->
                "Read left to right and the values are still ${arg(0)} — exactly as before " +
                    "every rotation. That is why the result is still a search tree, and " +
                    "why the tree can be reshaped whenever it needs to be."

            NarrationId.AVL_WATCH_SUMMARY ->
                "${arg(0)} rotations, and ${arg(2)} nodes in ${arg(1)} levels."

            NarrationId.AVL_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.AVL_IDEA_1 -> "Balance factor is left height minus right height."
            NarrationId.AVL_IDEA_2 -> "Rotate the lowest node that reaches ±2. Never more than one."
            NarrationId.AVL_IDEA_3 -> "Path runs straight? The child comes up — one rotation."
            NarrationId.AVL_IDEA_4 -> "Path bends? The grandchild comes up — two rotations."
            NarrationId.AVL_IDEA_5 ->
                "A BST can degrade to O(n). An AVL tree never lets it: search stays " +
                    "O(log n), guaranteed."

            // ── Dijkstra ──────────────────────────────────────────────────
            // Every line names the reason — "the cheapest", "a shorter route" —
            // rather than the control. The learner should be able to say why,
            // not just what they tapped.
            NarrationId.DIJ_OPTION_NODE -> arg(0)
            NarrationId.DIJ_OPTION_VALUE -> arg(0)
            NarrationId.DIJ_ASK_SELECT -> "Which node does Dijkstra process next?"
            NarrationId.DIJ_ASK_RELAX ->
                "${arg(2)} is ${arg(3)} away from ${arg(0)}, so that route costs ${arg(4)}. " +
                    "${arg(0)} is currently ${arg(1)}. What should it be?"

            NarrationId.DIJ_SELECTED -> "Processing ${arg(0)}, at ${arg(1)}."
            NarrationId.DIJ_EXAMINED ->
                "${arg(0)} is ${arg(1)}, and ${arg(0)}→${arg(4)} costs ${arg(2)}. That makes ${arg(3)}."

            NarrationId.DIJ_FIRST_REACH ->
                "${arg(0)} had no distance at all, so ${arg(1)} becomes its first."

            NarrationId.DIJ_UPDATED -> "${arg(0)}: ${arg(2)} → ${arg(1)}."
            NarrationId.DIJ_KEPT -> "${arg(0)} stays at ${arg(2)}."
            NarrationId.DIJ_SKIP_SETTLED ->
                "${arg(0)} is already settled at ${arg(1)} — nothing can improve it now."

            NarrationId.DIJ_SETTLED -> "${arg(0)} is settled at ${arg(1)}."
            NarrationId.DIJ_TARGET_SETTLED ->
                "${arg(0)} is settled at ${arg(1)} — and it is the target."

            NarrationId.DIJ_HINT_SELECT ->
                "Read the distances. Dijkstra always continues from the cheapest node " +
                    "it has reached but not settled."

            NarrationId.DIJ_HINT_RELAX ->
                "${arg(0)} is ${arg(1)} away, and this edge costs ${arg(2)}, so the route " +
                    "through it costs ${arg(3)}. Is that better than what is already known?"

            NarrationId.DIJ_RETRY_SELECT_LOOK -> "Look at the distances again."
            NarrationId.DIJ_RETRY_SELECT_ASK ->
                "Which node has been reached, is not settled, and has the smallest distance?"

            NarrationId.DIJ_RETRY_SELECT_EXPLAIN ->
                "${arg(0)} is at ${arg(1)}, and nothing unsettled is closer. That is the one " +
                    "Dijkstra takes."

            NarrationId.DIJ_RETRY_RELAX_LOOK ->
                "That route costs ${arg(0)}. ${arg(1)} is already ${arg(2)}."

            NarrationId.DIJ_RETRY_RELAX_ASK_BETTER ->
                "${arg(0)} is smaller than ${arg(1)}. Which of them is the better route?"

            NarrationId.DIJ_RETRY_RELAX_ASK_WORSE ->
                "${arg(0)} is not smaller than ${arg(1)}. Does anything need to change?"

            NarrationId.DIJ_RETRY_RELAX_EXPLAIN_UPDATE ->
                "${arg(0)} beats ${arg(1)}, so ${arg(2)} takes the shorter route: ${arg(0)}."

            NarrationId.DIJ_RETRY_RELAX_EXPLAIN_KEEP ->
                "${arg(0)} is no better than ${arg(1)}, so ${arg(2)} keeps ${arg(1)}. " +
                    "Examining an edge does not mean changing anything."

            // Each wrong tap is the misconception it is.
            NarrationId.DIJ_WHY_ALREADY_SETTLED ->
                "${arg(0)} is already settled. Its distance is final and Dijkstra never " +
                    "goes back to it."

            NarrationId.DIJ_WHY_UNREACHED ->
                "${arg(0)} has no distance yet — nothing has reached it. Dijkstra only " +
                    "continues from nodes it can already get to."

            NarrationId.DIJ_WHY_NOT_CHEAPEST ->
                "${arg(0)} is ${arg(1)} away, but ${arg(2)} is only ${arg(3)}. Dijkstra always " +
                    "takes the cheapest node it knows about."

            // The classic slip: taking the edge and forgetting where you already are.
            NarrationId.DIJ_WHY_WEIGHT_ONLY ->
                "${arg(0)} is what the edge costs, not what the route costs. Add it to " +
                    "${arg(2)}'s own distance: that is ${arg(4)}."

            NarrationId.DIJ_WHY_MISSED_IMPROVEMENT ->
                "${arg(4)} is shorter than ${arg(5)}, so leaving ${arg(1)} where it is would " +
                    "keep the longer route."

            NarrationId.DIJ_WHY_WORSE_ROUTE ->
                "${arg(4)} is not shorter than ${arg(5)}, so this route is no improvement. " +
                    "A distance only ever goes down."

            NarrationId.DIJ_CORRECT_SELECT ->
                "${arg(0)} at ${arg(1)} — nothing unsettled is closer, so nothing can reach " +
                    "it more cheaply later."

            NarrationId.DIJ_CORRECT_UPDATE ->
                "${arg(1)} beats ${arg(2)}, so ${arg(0)} takes the route through ${arg(3)}."

            NarrationId.DIJ_CORRECT_KEEP ->
                "${arg(1)} is no better than ${arg(2)}, so ${arg(0)} keeps what it had."

            // ── Dijkstra — WATCH ──────────────────────────────────────────
            NarrationId.DIJ_WATCH_SETUP ->
                "Find the cheapest route from ${arg(0)} to ${arg(1)}."

            NarrationId.DIJ_WATCH_SETUP_SUPPORT ->
                "Every node starts at ∞ except ${arg(0)}, which is 0. We know nothing yet " +
                    "except where we are."

            NarrationId.DIJ_WATCH_SELECT -> "Process ${arg(0)}, at ${arg(1)}."
            NarrationId.DIJ_WATCH_SELECT_WHY ->
                "Nothing unsettled is closer, so no route found later could beat it."

            NarrationId.DIJ_WATCH_SELECT_IMPROVED_WHY ->
                "${arg(0)} got to ${arg(1)} by being beaten down, not by being reached first — " +
                    "the route through ${arg(2)} turned out cheaper."

            NarrationId.DIJ_WATCH_REACH -> "${arg(0)} is reached: ${arg(1)}."
            NarrationId.DIJ_WATCH_REACH_WHY ->
                "${arg(0)} is ${arg(1)} and the edge costs ${arg(2)}, so the route costs " +
                    "${arg(3)}. Anything beats ∞, so there is nothing to compare yet."

            NarrationId.DIJ_WATCH_UPDATE -> "${arg(0)}: ${arg(1)} → ${arg(2)}."
            NarrationId.DIJ_WATCH_UPDATE_WHY ->
                "${arg(0)} is ${arg(1)} and this edge costs ${arg(2)}, so the route costs " +
                    "${arg(3)} — shorter than ${arg(4)}. The old distance was only ever a claim."

            NarrationId.DIJ_WATCH_KEEP -> "${arg(0)} stays at ${arg(1)}."
            NarrationId.DIJ_WATCH_KEEP_WHY ->
                "${arg(0)} is ${arg(1)} and this edge costs ${arg(2)}, so this route costs " +
                    "${arg(3)} — no better than ${arg(4)}. Examining an edge does not mean " +
                    "changing anything."

            NarrationId.DIJ_WATCH_DONE -> "${arg(0)} is settled at ${arg(1)}. Done."
            NarrationId.DIJ_WATCH_DONE_WHY ->
                "Once ${arg(0)} is the cheapest thing left, no route still being explored " +
                    "could reach it for less."

            NarrationId.DIJ_WATCH_INSIGHT -> "A distance is a claim, until something beats it."
            NarrationId.DIJ_WATCH_INSIGHT_SUPPORT ->
                "Taking the cheapest node is safe because every edge costs something: any " +
                    "route through a node still unsettled is already at least as long. That " +
                    "one sentence is the whole proof — and it is exactly what a negative " +
                    "edge would break, which is why Dijkstra needs positive weights."

            NarrationId.DIJ_WATCH_SUMMARY -> "${arg(0)} — ${arg(1)}."
            NarrationId.DIJ_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.DIJ_IDEA_1 -> "Start at 0; everything else is ∞ until it is reached."
            NarrationId.DIJ_IDEA_2 -> "Always continue from the cheapest node not yet settled."
            NarrationId.DIJ_IDEA_3 -> "Relax every edge: distance + weight, and keep the smaller."
            NarrationId.DIJ_IDEA_4 -> "O((V + E) log V) with a heap; O(V) space. Positive weights only."
            NarrationId.DIJ_IDEA_5 ->
                "DFS goes deep, BFS goes level by level, Dijkstra goes by distance — and " +
                    "where every edge costs 1, that is BFS."

            // ── 0/1 Knapsack ──────────────────────────────────────────────
            // TAKE and SKIP in capitals, because they are the two words on the
            // buttons. Every cell is printed as dp[i][c] beside what it means.
            NarrationId.KN_OPTION_TAKE -> "TAKE"
            NarrationId.KN_OPTION_SKIP -> "SKIP"
            NarrationId.KN_OPTION_TAKEN -> "Taken"
            NarrationId.KN_OPTION_LEFT_OUT -> "Left out"
            NarrationId.KN_OPTION_CELL -> "dp[${arg(0)}][${arg(1)}]"
            NarrationId.KN_ASK_SOURCE ->
                "If you TAKE the ${arg(0)}, which cell holds the best for the room that is left?"
            NarrationId.KN_ASK_CHOICE -> "SKIP or TAKE the ${arg(0)}?"
            NarrationId.KN_ASK_TRACE -> "Was the ${arg(0)} taken?"

            // Act I — the problem, before any table exists (ADR-053). The numbers
            // in every line are the engine's, so a different bag says different
            // things rather than the same things wrongly.
            NarrationId.KN_OPTION_ALL -> "All of it"
            NarrationId.KN_OPTION_SOME -> "Only some of it"
            NarrationId.KN_OPTION_ONCE -> "Once, or not at all"
            NarrationId.KN_OPTION_MANY -> "As many as fit"
            NarrationId.KN_OPTION_FRACTION -> "Any fraction of it"
            NarrationId.KN_OPTION_ITEM -> "${arg(0)} · ${arg(1)} kg"
            NarrationId.KN_OPTION_NOTHING -> "Nothing else fits"
            NarrationId.KN_OPTION_BAG -> "${arg(0)}"
            NarrationId.KN_ASK_CAPACITY -> "The bag holds ${arg(0)} kg. How much of this can you take?"
            NarrationId.KN_ASK_TIMES -> "How many times can the ${arg(0)} go in the bag?"
            NarrationId.KN_ASK_FITS ->
                "The ${arg(0)} is in, and ${arg(1)} kg is left. What else still fits?"
            NarrationId.KN_ASK_BETTER -> "Two bags. Which one is worth more?"
            NarrationId.KN_INTRO_ITEMS ->
                "${arg(0)} things you could take, and a bag that holds ${arg(1)} kg."
            NarrationId.KN_INTRO_TOO_MUCH ->
                "${arg(0)} kg of things, ${arg(1)} kg of bag. Something stays behind."
            NarrationId.KN_INTRO_ONCE -> "Each item goes in once, or not at all."
            NarrationId.KN_INTRO_PACKING ->
                "The ${arg(0)} is the most valuable: ${arg(1)}. It weighs ${arg(2)}, so ${arg(3)} kg " +
                    "is left."
            NarrationId.KN_INTRO_PACKED -> "${arg(0)}: ${arg(1)} kg, worth ${arg(2)}."
            NarrationId.KN_INTRO_COMPARED ->
                "${arg(0)} is worth ${arg(1)} — more than ${arg(2)}."
            NarrationId.KN_INTRO_EVERY_BAG ->
                "${arg(0)} possible bags from ${arg(1)} items, and each new item doubles it."
            NarrationId.KN_INTRO_FORK ->
                "So take the items one at a time: TAKE or SKIP, and keep the better bag."
            // The table arrives and is explained before it is filled: what a box
            // holds, what a box means, and only then what a box is called. The
            // shorthand is introduced as a name for something already understood.
            NarrationId.KN_INTRO_GRID -> "A grid of ${arg(0)} boxes. Each one holds a single number."
            NarrationId.KN_INTRO_AXES ->
                "A row is which items you may use; a column is how much room you have."
            NarrationId.KN_INTRO_NAME ->
                "That box is called dp[${arg(0)}][${arg(1)}] — row ${arg(0)}, column ${arg(1)}."
            NarrationId.KN_WATCH_GRID -> "Now a table. Every box will hold one number."
            NarrationId.KN_WATCH_GRID_SUPPORT ->
                "${arg(0)} boxes, and every one of them answers the same question: what is the " +
                    "best value you can fit? Not which items — just what the best bag is worth."
            NarrationId.KN_WATCH_AXES ->
                "A row is which items you may use. A column is how much room you have."
            NarrationId.KN_WATCH_AXES_SUPPORT ->
                "So the box where they cross means: the best you can do using only ${arg(0)}, " +
                    "with ${arg(1)} kg of room."
            NarrationId.KN_WATCH_NAME -> "That box has a short name: dp[${arg(0)}][${arg(1)}]."
            NarrationId.KN_WATCH_NAME_SUPPORT ->
                "dp[row][column], and nothing more. The name says which box; the sentence above " +
                    "says what it means."
            NarrationId.KN_HINT_CAPACITY -> "Add up what the items weigh, and compare it with the bag."
            NarrationId.KN_HINT_TIMES -> "The lesson is called 0/1. That is the answer, as two numbers."
            NarrationId.KN_HINT_FITS -> "Only ${arg(0)} kg is left. Which weights are small enough?"
            NarrationId.KN_HINT_BETTER -> "Add up the values in each bag."
            NarrationId.KN_RETRY_CAPACITY_LOOK ->
                "The items weigh ${arg(0)} kg. The bag holds ${arg(1)}."
            NarrationId.KN_RETRY_CAPACITY_ASK ->
                "Does ${arg(0)} kg go into a bag that holds ${arg(1)}?"
            NarrationId.KN_RETRY_CAPACITY_EXPLAIN ->
                "${arg(0)} is ${arg(2)} more than ${arg(1)}, so only some of it can go in. That is " +
                    "what makes this a problem at all."
            NarrationId.KN_RETRY_TIMES_LOOK ->
                "There is one ${arg(0)}, and it is either in the bag or it is not."
            NarrationId.KN_RETRY_TIMES_ASK ->
                "The lesson is called 0/1 Knapsack. What could the 0 and the 1 be counting?"
            NarrationId.KN_RETRY_TIMES_EXPLAIN ->
                "Once, or not at all: 1 means the ${arg(0)} is in, 0 means it is out. That is the 0/1."
            NarrationId.KN_RETRY_FITS_LOOK ->
                "${arg(0)} kg of bag, minus the ${arg(1)} already used, leaves ${arg(2)}."
            NarrationId.KN_RETRY_FITS_ASK -> "Which of the others weighs ${arg(0)} kg or less?"
            NarrationId.KN_RETRY_FITS_EXPLAIN ->
                "The ${arg(0)} weighs ${arg(1)}, and ${arg(2)} kg is left. It is the only one that fits."
            NarrationId.KN_RETRY_BETTER_LOOK -> "One bag is ${arg(0)}. The other is ${arg(1)}."
            NarrationId.KN_RETRY_BETTER_ASK -> "Add each one up. Which total is larger?"
            NarrationId.KN_RETRY_BETTER_EXPLAIN ->
                "${arg(0)} is worth ${arg(1)}, and ${arg(2)} is worth ${arg(3)}."
            NarrationId.KN_WHY_ALL_FITS ->
                "${arg(0)} kg will not go into a bag that holds ${arg(1)}. If everything fitted " +
                    "there would be nothing to decide."
            NarrationId.KN_WHY_MANY ->
                "There is only one ${arg(0)}. Taking a second copy of it is a different problem — " +
                    "the unbounded knapsack, not this one."
            NarrationId.KN_WHY_FRACTION ->
                "Half a ${arg(0)} is not worth ${arg(1)} — it is not worth anything. Splitting " +
                    "items is the fractional knapsack, and this is not it."
            NarrationId.KN_WHY_TOO_HEAVY ->
                "The ${arg(0)} weighs ${arg(1)}, and only ${arg(2)} kg is left."
            NarrationId.KN_WHY_SOMETHING_FITS ->
                "The ${arg(0)} weighs ${arg(1)}, and ${arg(2)} kg is left. It fits."
            NarrationId.KN_WHY_WORSE_BAG ->
                "${arg(0)} is worth ${arg(1)}. ${arg(2)} is worth ${arg(3)}, and it fits too."
            NarrationId.KN_CORRECT_CAPACITY ->
                "Right — ${arg(0)} kg will not go into ${arg(1)}. So every item is a decision."
            NarrationId.KN_CORRECT_TIMES ->
                "Right. One ${arg(0)}, in or out: 1 or 0. That is the 0/1 in the name."
            NarrationId.KN_CORRECT_FITS ->
                "Right — the ${arg(0)} weighs ${arg(1)}, and that leaves ${arg(2)} kg."
            NarrationId.KN_CORRECT_BETTER ->
                "Right: ${arg(0)} is worth ${arg(1)}, and the bag you packed was worth ${arg(2)}. " +
                    "Taking the most valuable thing first is not enough."
            NarrationId.KN_WATCH_BAG -> "A bag that holds ${arg(0)} kg."
            NarrationId.KN_WATCH_BAG_SUPPORT -> "The job: put the most value into it."
            NarrationId.KN_WATCH_ITEMS -> "${arg(0)} things you could take."
            NarrationId.KN_WATCH_ITEMS_SUPPORT ->
                "Each has a weight and a value. The weight is what it costs you; the value is " +
                    "what it is worth."
            NarrationId.KN_WATCH_TOO_MUCH ->
                "Together they weigh ${arg(0)} kg, and the bag holds ${arg(1)}."
            NarrationId.KN_WATCH_TOO_MUCH_SUPPORT ->
                "So you cannot take everything, and every item becomes a decision."
            NarrationId.KN_WATCH_ONCE -> "Each item goes in once, or not at all."
            NarrationId.KN_WATCH_ONCE_SUPPORT ->
                "1 means take it, 0 means leave it — that is the 0/1 in the name. No second copy, " +
                    "and no half an item."
            NarrationId.KN_WATCH_PACKING ->
                "Start with the most valuable: the ${arg(0)}, worth ${arg(1)}."
            NarrationId.KN_WATCH_PACKING_SUPPORT ->
                "It weighs ${arg(0)}, so ${arg(1)} − ${arg(2)} = ${arg(3)} kg is left."
            NarrationId.KN_WATCH_PACKED -> "${arg(0)} — ${arg(1)} kg, worth ${arg(2)}."
            NarrationId.KN_WATCH_PACKED_SUPPORT ->
                "The bag is full. Is ${arg(0)} the best it can do?"
            NarrationId.KN_WATCH_COMPARED -> "${arg(0)} is worth ${arg(1)}."
            NarrationId.KN_WATCH_COMPARED_SUPPORT ->
                "It weighs ${arg(0)} kg and is worth more than ${arg(1)}. Taking the most " +
                    "valuable thing first was wrong."
            NarrationId.KN_WATCH_EVERY_BAG -> "Why not try every bag? There are ${arg(0)}."
            NarrationId.KN_WATCH_EVERY_BAG_SUPPORT ->
                "${arg(0)} items make ${arg(1)} bags, and each new item doubles it. At 30 items " +
                    "that is over a billion."
            NarrationId.KN_WATCH_FORK -> "So ask one item at a time: TAKE it, or SKIP it?"
            NarrationId.KN_WATCH_FORK_SUPPORT ->
                "TAKE spends its weight and adds its value. SKIP keeps what you had. Keep " +
                    "whichever bag is worth more — and write the answer down, so it is never " +
                    "worked out twice."
            NarrationId.KN_BASE -> "No items, or no room, is worth 0."
            NarrationId.KN_FOCUS ->
                "dp[${arg(0)}][${arg(1)}]: the best using ${arg(2)}, with capacity ${arg(1)}."
            NarrationId.KN_SOURCE ->
                "TAKE: ${arg(0)} + dp[${arg(1)}][${arg(2)}] = ${arg(0)} + ${arg(3)} = ${arg(4)}. " +
                    "SKIP keeps ${arg(5)}."
            NarrationId.KN_TOOK -> "dp[${arg(0)}][${arg(1)}] = ${arg(2)} — the ${arg(3)} goes in."
            NarrationId.KN_SKIPPED ->
                "dp[${arg(0)}][${arg(1)}] = ${arg(2)} — the bag is better without the ${arg(3)}."
            NarrationId.KN_COPIED ->
                "dp[${arg(0)}][${arg(1)}] = ${arg(2)} — the ${arg(3)} weighs ${arg(4)} and does not fit."
            NarrationId.KN_TRACE_BEGIN ->
                "The best bag is worth ${arg(0)}. Now walk back up to find what is in it."
            NarrationId.KN_MARKED_TAKEN ->
                "The ${arg(0)} is in. It used ${arg(1)}, so ${arg(2)} capacity is left to explain."
            NarrationId.KN_MARKED_LEFT_OUT ->
                "The ${arg(0)} is out. Still ${arg(1)} capacity to explain, one row up."
            NarrationId.KN_HINT_SOURCE ->
                "TAKE uses up the item's weight, and builds on a row that has never seen the item."
            NarrationId.KN_HINT_CHOICE ->
                "Check the weight against the capacity, then compare what each choice is worth."
            NarrationId.KN_HINT_TRACE -> "Compare this cell with the one directly above it."
            NarrationId.KN_RETRY_SOURCE_LOOK ->
                "The ${arg(0)} weighs ${arg(1)}. Look at the row above."
            NarrationId.KN_RETRY_SOURCE_ASK ->
                "Capacity ${arg(0)}, minus ${arg(1)} for the ${arg(2)} — how much room is left, " +
                    "and which row has not seen the ${arg(2)} yet?"
            NarrationId.KN_RETRY_SOURCE_EXPLAIN ->
                "Taking the ${arg(0)} leaves ${arg(1)} − ${arg(2)} = ${arg(3)} capacity. The best " +
                    "for that, without the ${arg(0)}, is dp[${arg(4)}][${arg(3)}]."
            NarrationId.KN_RETRY_FIT_LOOK ->
                "The ${arg(0)} weighs ${arg(1)}. This column's capacity is ${arg(2)}."
            NarrationId.KN_RETRY_FIT_ASK ->
                "Can something that weighs ${arg(0)} go in a bag that holds ${arg(1)}?"
            NarrationId.KN_RETRY_FIT_EXPLAIN ->
                "${arg(1)} is more than ${arg(2)}, so the ${arg(0)} cannot go in. SKIP, and the " +
                    "cell copies the ${arg(3)} above."
            NarrationId.KN_RETRY_CHOICE_LOOK -> "TAKE is worth ${arg(0)}. SKIP keeps ${arg(1)}."
            NarrationId.KN_RETRY_CHOICE_ASK ->
                "The cell keeps whichever bag is worth more. Which one is that?"
            NarrationId.KN_RETRY_CHOICE_EXPLAIN_TAKE ->
                "${arg(0)} is more than ${arg(1)}, so TAKE the ${arg(2)}."
            NarrationId.KN_RETRY_CHOICE_EXPLAIN_SKIP ->
                "${arg(0)} is no more than ${arg(1)}, so SKIP the ${arg(2)} and keep ${arg(1)}."
            NarrationId.KN_RETRY_TRACE_LOOK ->
                "dp[${arg(0)}][${arg(1)}] is ${arg(2)}. dp[${arg(3)}][${arg(1)}] is ${arg(4)}."
            NarrationId.KN_RETRY_TRACE_ASK ->
                "If the ${arg(0)} row did not change the value, did the ${arg(0)} go in?"
            NarrationId.KN_RETRY_TRACE_EXPLAIN_TAKEN ->
                "${arg(0)} is not ${arg(1)} — the ${arg(2)} row raised it, so the ${arg(2)} was taken."
            NarrationId.KN_RETRY_TRACE_EXPLAIN_LEFT_OUT ->
                "${arg(0)} is the same as the row above, so the ${arg(2)} was left out."
            NarrationId.KN_WHY_SAME_ROW ->
                "Row ${arg(0)} is allowed to use the ${arg(1)} already. Building on it could put " +
                    "the ${arg(1)} in twice — and 0/1 means once. TAKE builds on the row above."
            NarrationId.KN_WHY_SKIP_CELL ->
                "That is SKIP's cell — no room used. Taking the ${arg(0)} uses ${arg(1)}, so TAKE " +
                    "starts from less room."
            NarrationId.KN_WHY_WRONG_CAPACITY ->
                "Taking the ${arg(0)} leaves ${arg(1)} − ${arg(2)} = ${arg(3)} capacity, not ${arg(4)}."
            NarrationId.KN_WHY_ROW_TOO_HIGH ->
                "Row ${arg(0)} has never seen the ${arg(1)}. The row directly above holds the best " +
                    "of everything before this item."
            NarrationId.KN_WHY_NO_FIT ->
                "The ${arg(0)} weighs ${arg(1)}, but only ${arg(2)} capacity is available. " +
                    "It cannot go in."
            NarrationId.KN_WHY_SKIP_LOSES ->
                "Skipping keeps ${arg(0)}. Taking gives ${arg(1)} + ${arg(2)} = ${arg(3)}. " +
                    "Which choice keeps the better bag?"
            NarrationId.KN_WHY_TAKE_LOSES ->
                "Taking gives ${arg(0)} + ${arg(1)} = ${arg(2)}, but skipping keeps ${arg(3)} — " +
                    "a bag at least as good without the ${arg(4)}. The cell keeps the larger."
            NarrationId.KN_WHY_TRACE_SAME ->
                "dp[${arg(0)}][${arg(1)}] and dp[${arg(3)}][${arg(1)}] are both ${arg(2)}. The " +
                    "${arg(5)} row changed nothing, so the ${arg(5)} was left out."
            NarrationId.KN_WHY_TRACE_CHANGED ->
                "dp[${arg(0)}][${arg(1)}] is ${arg(2)} but dp[${arg(3)}][${arg(1)}] is ${arg(4)}. " +
                    "That difference only exists if the ${arg(5)} went in."
            NarrationId.KN_CORRECT_SOURCE ->
                "Right: with the ${arg(0)} in, ${arg(1)} capacity is left, and " +
                    "dp[${arg(2)}][${arg(1)}] = ${arg(3)} is the best for it."
            NarrationId.KN_CORRECT_NO_FIT ->
                "The ${arg(0)} doesn't fit in ${arg(1)}, so the best is still the cell above: ${arg(2)}."
            NarrationId.KN_CORRECT_TAKE ->
                "${arg(0)} + ${arg(1)} = ${arg(2)} beats ${arg(3)}: the ${arg(4)} goes in."
            NarrationId.KN_CORRECT_SKIP ->
                "${arg(0)} doesn't beat ${arg(1)}: the bag is at least as good without the ${arg(2)}."
            NarrationId.KN_CORRECT_TAKEN ->
                "The ${arg(0)} was taken: ${arg(1)} − ${arg(2)} = ${arg(3)} capacity left to explain."
            NarrationId.KN_CORRECT_LEFT_OUT ->
                "The ${arg(0)} was left out: still ${arg(1)} capacity to explain, one row up."
            NarrationId.KN_WATCH_SETUP -> "Pack the most value into a bag that holds ${arg(0)}."
            NarrationId.KN_WATCH_SETUP_SUPPORT ->
                "${arg(0)} ${plural(0, "item", "items")}, each with a weight and a value."
            NarrationId.KN_WATCH_DEFINE -> "Row 0 and column 0 are all 0."
            NarrationId.KN_WATCH_DEFINE_SUPPORT ->
                "No items, or no room, is worth nothing. Every other box gets worked out from " +
                    "the boxes above it."
            NarrationId.KN_WATCH_FIRST_ROW -> "Row 1 — only the ${arg(0)}."
            NarrationId.KN_WATCH_FIRST_ROW_SUPPORT ->
                "0 while it doesn't fit, then ${arg(0)} from capacity ${arg(1)} up."
            NarrationId.KN_WATCH_NO_FIT ->
                "The ${arg(0)} weighs ${arg(1)}. Up to capacity ${arg(2)}, it can't go in."
            NarrationId.KN_WATCH_NO_FIT_SUPPORT ->
                "A cell whose item doesn't fit copies the cell above: the best without it."
            NarrationId.KN_WATCH_SKIP_SIDE ->
                "Capacity ${arg(0)}. SKIP the ${arg(1)}: keep dp[${arg(2)}][${arg(0)}] = ${arg(3)}."
            NarrationId.KN_WATCH_SKIP_SIDE_SUPPORT ->
                "The best bag without the ${arg(0)}, already worked out one row up."
            NarrationId.KN_WATCH_TAKE_SIDE ->
                "TAKE the ${arg(0)}: ${arg(1)} + dp[${arg(2)}][${arg(3)}] = ${arg(4)}."
            NarrationId.KN_WATCH_TAKE_SIDE_SUPPORT ->
                "The ${arg(0)}, plus the best of the ${arg(1)} capacity it leaves."
            NarrationId.KN_WATCH_MAX ->
                "${arg(0)} beats ${arg(1)}, so dp[${arg(2)}][${arg(3)}] = ${arg(0)}."
            NarrationId.KN_WATCH_MAX_SUPPORT -> "Every cell keeps the larger of its two choices."
            NarrationId.KN_WATCH_REUSE ->
                "Capacity ${arg(0)}. TAKE the ${arg(1)}, and ${arg(2)} capacity is left."
            NarrationId.KN_WATCH_REUSE_SUPPORT ->
                "dp[${arg(0)}][${arg(1)}] already knows the best for ${arg(1)}: ${arg(2)}. It was " +
                    "worked out once, and is simply read back."
            NarrationId.KN_WATCH_REUSE_RESULT ->
                "${arg(0)} + ${arg(1)} = ${arg(2)} beats ${arg(3)}. dp[${arg(4)}][${arg(5)}] = ${arg(2)}."
            NarrationId.KN_WATCH_REUSE_RESULT_SUPPORT -> "That cell means: the ${arg(0)}, together."
            NarrationId.KN_WATCH_ROW_SUMMARY ->
                "The ${arg(0)} weighs ${arg(1)}: it copies the row above until it fits, then asks " +
                    "the same question."
            NarrationId.KN_WATCH_ROW_SUMMARY_SUPPORT ->
                "dp[${arg(0)}][${arg(1)}] = ${arg(2)}."
            NarrationId.KN_WATCH_LAST_CELL ->
                "The last cell. SKIP keeps ${arg(0)}. TAKE gives ${arg(1)}."
            NarrationId.KN_WATCH_LAST_CELL_SUPPORT ->
                "TAKE is ${arg(0)} + dp[${arg(1)}][${arg(2)}] = ${arg(0)} + ${arg(3)}."
            NarrationId.KN_WATCH_ANSWER_SKIP ->
                "${arg(1)} doesn't beat ${arg(0)} — leave the ${arg(2)} out."
            NarrationId.KN_WATCH_ANSWER_TAKE ->
                "${arg(0)} beats ${arg(1)} — the ${arg(2)} goes in."
            NarrationId.KN_WATCH_ANSWER_COPY ->
                "The ${arg(0)} doesn't fit, so the answer is the cell above: ${arg(1)}."
            NarrationId.KN_WATCH_ANSWER_GREEDY_SUPPORT ->
                "Taking the most valuable thing first put the ${arg(0)} in. The table leaves it " +
                    "out, and that is the whole difference."
            NarrationId.KN_WATCH_ANSWER_SUPPORT ->
                "The best bag for the whole problem is worth ${arg(0)}."
            NarrationId.KN_WATCH_TRACE_START -> "The best bag is worth ${arg(0)}. But which items?"
            NarrationId.KN_WATCH_TRACE_TAKEN -> "The ${arg(0)} made the difference."
            NarrationId.KN_WATCH_TRACE_LEFT_OUT -> "The ${arg(0)} changed nothing."
            NarrationId.KN_WATCH_TRACE_TAKEN_SUPPORT ->
                "dp[${arg(0)}][${arg(1)}] = ${arg(2)}, but dp[${arg(3)}][${arg(1)}] = ${arg(4)}: " +
                    "the ${arg(5)} is in, and ${arg(6)} capacity is left."
            NarrationId.KN_WATCH_TRACE_LEFT_OUT_SUPPORT ->
                "dp[${arg(0)}][${arg(1)}] and dp[${arg(3)}][${arg(1)}] are both ${arg(2)}: the " +
                    "${arg(5)} was left out."
            NarrationId.KN_WATCH_TRACE_DONE -> "${arg(0)}: weight ${arg(1)}, value ${arg(2)}."
            NarrationId.KN_WATCH_INSIGHT -> "Every cell is a smaller bag, solved once."
            NarrationId.KN_WATCH_INSIGHT_SUPPORT ->
                "Grabbing the most valuable item first got ${arg(0)}. The table found ${arg(1)}."
            NarrationId.KN_WATCH_INSIGHT_SUPPORT_PLAIN ->
                "Each cell reads two cells from the row above — never the whole problem again."
            NarrationId.KN_WATCH_SUMMARY ->
                "${arg(0)} items, capacity ${arg(1)}, ${arg(2)} cells."
            NarrationId.KN_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.KN_IDEA_1 -> "0/1: every item is taken once, or left."
            NarrationId.KN_IDEA_2 -> "dp[i][c] is the best value using the first i items with capacity c."
            NarrationId.KN_IDEA_3 ->
                "SKIP keeps the cell above. TAKE adds the item to the row above, at the capacity " +
                    "left. Keep the larger."
            NarrationId.KN_IDEA_4 ->
                "Walk back up: where a cell differs from the one above, that item was taken."
            NarrationId.KN_IDEA_5 ->
                "O(n × W) time and space — one cell per item and capacity, each decided once."

            // ── Binary Tree — Inorder: LEFT → NODE → RIGHT ────────────────
            // The learner taps nodes, so an "option" label is just the value.
            NarrationId.INORDER_ASK -> "${arg(0)}"
            NarrationId.INORDER_HINT ->
                "Inorder finishes the whole left subtree, then the node, then the right."

            NarrationId.INORDER_RETRY_LOOK -> "Look at what is still owed below ${arg(0)}."
            NarrationId.INORDER_RETRY_ASK ->
                "Is anything left of ${arg(0)} still unvisited? Nothing comes out before it."

            NarrationId.INORDER_RETRY_EXPLAIN ->
                "Left, then the node, then right — so from ${arg(0)} the next thing is " +
                    "${arg(1)}."

            NarrationId.INORDER_WHY_VISITED ->
                "${arg(0)} is already visited — it is in the output."

            NarrationId.INORDER_WHY_NOT_ADJACENT ->
                "A traversal only moves to a child or back to a parent. It cannot jump " +
                    "to ${arg(0)} from ${arg(1)}."

            // The line this lesson exists for.
            NarrationId.INORDER_WHY_LEFT_FIRST ->
                "Not yet. Everything in ${arg(0)}'s left subtree is visited before " +
                    "${arg(0)} itself."

            NarrationId.INORDER_WHY_NODE_BEFORE_RIGHT ->
                "${arg(1)} has not been visited yet, and it comes before its right " +
                    "subtree."

            NarrationId.INORDER_WHY_LEFT_BEFORE_RIGHT ->
                "The left subtree comes first. ${arg(0)} is on the right of ${arg(1)}."

            NarrationId.INORDER_ON_VISIT -> "Visit ${arg(0)}."
            NarrationId.INORDER_ON_DESCEND -> "Down to ${arg(0)}."
            NarrationId.INORDER_ON_DESCEND_VISIT -> "Down to ${arg(0)}, and visit it."
            NarrationId.INORDER_ON_RETURN -> "${arg(0)} is finished. Back to ${arg(1)}."
            NarrationId.INORDER_CORRECT_VISIT ->
                "${arg(0)} had nothing unvisited to its left, so it comes out now."

            NarrationId.INORDER_CORRECT_DESCEND ->
                "Everything left of ${arg(1)} comes out before ${arg(1)} does."

            // ── Binary Tree — Inorder — WATCH ─────────────────────────────
            NarrationId.INORDER_WATCH_SETUP -> "Inorder: left subtree, node, right subtree."
            NarrationId.INORDER_WATCH_SETUP_SUPPORT ->
                "Nothing is emitted on the way down. A node waits until everything to " +
                    "its left is out."

            NarrationId.INORDER_WATCH_DOWN -> "Down to ${arg(0)}."
            NarrationId.INORDER_WATCH_DOWN_WHY ->
                "${arg(0)} has a left subtree of its own, so it has to wait too."

            NarrationId.INORDER_WATCH_DOWN_AND_VISIT -> "Down to ${arg(0)} — and visit it."
            NarrationId.INORDER_WATCH_DOWN_AND_VISIT_WHY ->
                "Nothing is to ${arg(0)}'s left, so nothing comes before it. This is where " +
                    "the output starts."

            NarrationId.INORDER_WATCH_VISIT -> "Visit ${arg(0)}."
            NarrationId.INORDER_WATCH_VISIT_WHY ->
                "${arg(0)}'s whole left subtree is out, so ${arg(0)} comes next — before " +
                    "anything on its right."

            NarrationId.INORDER_WATCH_RETURN -> "${arg(0)} is finished. Back to ${arg(1)}."
            NarrationId.INORDER_WATCH_RETURN_OWED ->
                "${arg(0)} is still on the stack, still owed its own visit."

            NarrationId.INORDER_WATCH_RETURN_DONE ->
                "${arg(0)} is already out, so the right subtree is what is left."

            NarrationId.INORDER_WATCH_INSIGHT -> "Every node waits for its whole left subtree."
            NarrationId.INORDER_WATCH_INSIGHT_SUPPORT ->
                "The output came out ${arg(0)} — sorted, because this tree is a search " +
                    "tree. Inorder is a traversal rule, not a sorting algorithm: run it on " +
                    "a tree that is not ordered and it will not come out sorted."

            NarrationId.INORDER_WATCH_SUMMARY -> "Inorder: ${arg(0)}."
            NarrationId.INORDER_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.INORDER_IDEA_1 -> "Left subtree first — all of it."
            NarrationId.INORDER_IDEA_2 -> "Then the node itself."
            NarrationId.INORDER_IDEA_3 -> "Then the right subtree."
            NarrationId.INORDER_IDEA_4 -> "O(n) time; O(h) space, for the stack of waiting nodes."

            // ── Binary Tree — Preorder: NODE → LEFT → RIGHT ───────────────
            NarrationId.PREORDER_ASK -> "${arg(0)}"
            NarrationId.PREORDER_HINT ->
                "Preorder emits a node the moment it reaches it, before either subtree."

            NarrationId.PREORDER_RETRY_LOOK -> "Look at ${arg(0)} itself before looking below it."
            NarrationId.PREORDER_RETRY_ASK ->
                "Has ${arg(0)} been emitted yet? In preorder that happens before anything " +
                    "under it."

            NarrationId.PREORDER_RETRY_EXPLAIN ->
                "Node, then left, then right — so from ${arg(0)} the next thing is ${arg(1)}."

            NarrationId.PREORDER_WHY_VISITED ->
                "${arg(0)} is already visited — it is in the output."

            NarrationId.PREORDER_WHY_NOT_ADJACENT ->
                "A traversal only moves to a child or back to a parent. It cannot jump " +
                    "to ${arg(0)} from ${arg(1)}."

            NarrationId.PREORDER_WHY_NODE_TOO_EARLY ->
                "${arg(0)} is already out. What is left is the subtrees below it."

            // The line this lesson exists for.
            NarrationId.PREORDER_WHY_NODE_FIRST ->
                "The node is visited first in preorder. ${arg(1)} comes out before " +
                    "anything below it."

            NarrationId.PREORDER_WHY_LEFT_BEFORE_RIGHT ->
                "The left subtree comes first. ${arg(0)} is on the right of ${arg(1)}."

            NarrationId.PREORDER_ON_VISIT -> "Visit ${arg(0)}."
            NarrationId.PREORDER_ON_DESCEND -> "Down to ${arg(0)}."
            NarrationId.PREORDER_ON_DESCEND_VISIT -> "Down to ${arg(0)}, and visit it."
            NarrationId.PREORDER_ON_RETURN -> "${arg(0)} is finished. Back to ${arg(1)}."
            NarrationId.PREORDER_CORRECT_VISIT ->
                "${arg(0)} comes out before either of its subtrees is looked at."

            NarrationId.PREORDER_CORRECT_DESCEND ->
                "${arg(1)} is already out, so the traversal moves down to ${arg(0)} — and " +
                    "emits it on arrival."

            // ── Binary Tree — Preorder — WATCH ────────────────────────────
            NarrationId.PREORDER_WATCH_SETUP -> "Preorder: node, left subtree, right subtree."
            NarrationId.PREORDER_WATCH_SETUP_SUPPORT ->
                "A node is emitted the moment the traversal reaches it — on the way down, " +
                    "never on the way back."

            NarrationId.PREORDER_WATCH_ROOT -> "Visit ${arg(0)} — the root, first of all."
            NarrationId.PREORDER_WATCH_ROOT_WHY ->
                "Nothing under ${arg(0)} has been looked at yet, and it does not have to " +
                    "be. The node comes first."

            NarrationId.PREORDER_WATCH_DOWN -> "Down to ${arg(0)}, and visit it."
            NarrationId.PREORDER_WATCH_DOWN_LEFT_WHY ->
                "${arg(0)} is out, so the left subtree is next — and its root is emitted " +
                    "on arrival too."

            NarrationId.PREORDER_WATCH_DOWN_RIGHT_WHY ->
                "${arg(0)}'s left subtree is finished, so the right one starts — same rule."

            NarrationId.PREORDER_WATCH_RETURN -> "${arg(0)} is finished. Back to ${arg(1)}."
            NarrationId.PREORDER_WATCH_RETURN_WHY ->
                "Nothing is emitted on the way up. Everything preorder does happens going down."

            NarrationId.PREORDER_WATCH_INSIGHT -> "The node is emitted on the way down."
            NarrationId.PREORDER_WATCH_INSIGHT_SUPPORT ->
                "The output starts at the root, ${arg(0)}, and every parent comes out before " +
                    "its children — which is exactly the order you would need to rebuild " +
                    "this tree from the sequence."

            NarrationId.PREORDER_WATCH_SUMMARY -> "Preorder: ${arg(0)}."
            NarrationId.PREORDER_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.PREORDER_IDEA_1 -> "The node first — before either subtree."
            NarrationId.PREORDER_IDEA_2 -> "Then the whole left subtree."
            NarrationId.PREORDER_IDEA_3 -> "Then the whole right subtree."
            NarrationId.PREORDER_IDEA_4 -> "O(n) time; O(h) space, for the stack of open nodes."

            // ── Binary Tree — Postorder: LEFT → RIGHT → NODE ──────────────
            NarrationId.POSTORDER_ASK -> "${arg(0)}"
            NarrationId.POSTORDER_HINT ->
                "Postorder emits a node only once both of its subtrees are completely done."

            NarrationId.POSTORDER_RETRY_LOOK -> "Look at what is still unvisited below ${arg(0)}."
            NarrationId.POSTORDER_RETRY_ASK ->
                "Are both of ${arg(0)}'s subtrees out yet? Until they are, ${arg(0)} waits."

            NarrationId.POSTORDER_RETRY_EXPLAIN ->
                "Left, then right, then the node — so from ${arg(0)} the next thing is " +
                    "${arg(1)}."

            NarrationId.POSTORDER_WHY_VISITED ->
                "${arg(0)} is already visited — it is in the output."

            NarrationId.POSTORDER_WHY_NOT_ADJACENT ->
                "A traversal only moves to a child or back to a parent. It cannot jump " +
                    "to ${arg(0)} from ${arg(1)}."

            // The line this lesson exists for.
            NarrationId.POSTORDER_WHY_CHILDREN_FIRST ->
                "Not yet. In postorder both child subtrees are visited before the parent, " +
                    "and ${arg(0)} still has one outstanding."

            NarrationId.POSTORDER_WHY_NODE_TOO_EARLY ->
                "${arg(1)} is not owed a visit yet — its subtrees are. ${arg(0)} is not " +
                    "the next one of them."

            NarrationId.POSTORDER_WHY_LEFT_BEFORE_RIGHT ->
                "The left subtree is finished first. ${arg(0)} is on the right of ${arg(1)}."

            NarrationId.POSTORDER_ON_VISIT -> "Visit ${arg(0)}."
            NarrationId.POSTORDER_ON_DESCEND -> "Down to ${arg(0)}."
            NarrationId.POSTORDER_ON_DESCEND_VISIT -> "Down to ${arg(0)}, and visit it."
            NarrationId.POSTORDER_ON_RETURN -> "${arg(0)} is finished. Back to ${arg(1)}."
            NarrationId.POSTORDER_CORRECT_VISIT ->
                "Both of ${arg(0)}'s subtrees are out, so now ${arg(0)} is."

            NarrationId.POSTORDER_CORRECT_DESCEND ->
                "${arg(1)} has to wait until everything below it is out, so the traversal " +
                    "goes down to ${arg(0)} first."

            // ── Binary Tree — Postorder — WATCH ───────────────────────────
            NarrationId.POSTORDER_WATCH_SETUP -> "Postorder: left subtree, right subtree, node."
            NarrationId.POSTORDER_WATCH_SETUP_SUPPORT ->
                "A node is emitted only once there is nothing left below it. Children " +
                    "first, parent last."

            NarrationId.POSTORDER_WATCH_DOWN -> "Down to ${arg(0)}."
            NarrationId.POSTORDER_WATCH_DOWN_WHY ->
                "Nothing is emitted on the way down — ${arg(0)} has subtrees of its own to " +
                    "finish first."

            NarrationId.POSTORDER_WATCH_DOWN_AND_VISIT -> "Down to ${arg(0)} — and visit it."
            NarrationId.POSTORDER_WATCH_LEAF_WHY ->
                "${arg(0)} has no subtrees to wait for, so it is out immediately. That is " +
                    "where the output starts."

            NarrationId.POSTORDER_WATCH_VISIT -> "Now visit ${arg(0)}."
            NarrationId.POSTORDER_WATCH_VISIT_WHY ->
                "Both of ${arg(0)}'s subtrees are out, so ${arg(0)} can finally follow them."

            NarrationId.POSTORDER_WATCH_VISIT_ROOT_WHY ->
                "The root, last of all — every other node in the tree is already out."

            NarrationId.POSTORDER_WATCH_RETURN -> "Back to ${arg(1)}."
            NarrationId.POSTORDER_WATCH_RETURN_NOT_YET ->
                "But not ${arg(0)} yet — it still has a right subtree that has not been " +
                    "touched."

            NarrationId.POSTORDER_WATCH_RETURN_NOW ->
                "${arg(0)} has nothing unvisited left below it now."

            NarrationId.POSTORDER_WATCH_INSIGHT -> "A node waits for everything beneath it."
            NarrationId.POSTORDER_WATCH_INSIGHT_SUPPORT ->
                "The root, ${arg(0)}, came out last — after every one of its descendants. " +
                    "That is why postorder is the order you delete a tree in, or evaluate " +
                    "an expression tree in: nothing is reached before what it depends on."

            NarrationId.POSTORDER_WATCH_SUMMARY -> "Postorder: ${arg(0)}."
            NarrationId.POSTORDER_WATCH_SUMMARY_SUPPORT -> "The rule"
            NarrationId.POSTORDER_IDEA_1 -> "The whole left subtree first."
            NarrationId.POSTORDER_IDEA_2 -> "Then the whole right subtree."
            NarrationId.POSTORDER_IDEA_3 -> "Then, last, the node itself."
            NarrationId.POSTORDER_IDEA_4 -> "O(n) time; O(h) space, for the stack of waiting parents."

            NarrationId.BS_LOOK_AT_MIDDLE -> "Look at the middle."
            NarrationId.BS_COMPARE_LESS -> "${arg(0)} < ${arg(1)}"
            NarrationId.BS_COMPARE_GREATER -> "${arg(0)} > ${arg(1)}"
            NarrationId.BS_COMPARE_EQUAL -> "${arg(0)} = ${arg(1)}"
            NarrationId.BS_ELIMINATED_LEFT -> "${arg(0)} possibilities eliminated."
            NarrationId.BS_ELIMINATED_RIGHT -> "${arg(0)} possibilities eliminated."
            NarrationId.BS_ASK_WHICH_HALF -> "Which half can contain ${arg(0)}?"
            NarrationId.BS_ASK_CHECK_MIDDLE -> "Which number should we check?"
            NarrationId.BS_OPTION_LEFT -> "LEFT"
            NarrationId.BS_OPTION_RIGHT -> "RIGHT"
            NarrationId.BS_OPTION_FOUND -> "FOUND"
            NarrationId.BS_OPTION_CHECK_MIDDLE -> "Check the middle"
            NarrationId.BS_HINT_COMPARE -> "Compare ${arg(0)} with ${arg(1)}."
            NarrationId.BS_WHY_LEFT_IMPOSSIBLE ->
                "${arg(0)} is greater than ${arg(1)}. Everything to the left is smaller still, " +
                    "so the left half cannot contain it."

            NarrationId.BS_WHY_RIGHT_IMPOSSIBLE ->
                "${arg(0)} is smaller than ${arg(1)}. Everything to the right is larger still, " +
                    "so the right half cannot contain it."

            NarrationId.BS_FOUND -> "Found ${arg(0)} at index ${arg(1)}."
            NarrationId.BS_NOT_FOUND -> "${arg(0)} was in the half you threw away."
            NarrationId.BS_RANGE_EMPTY -> "The range is empty. Target not found."
            NarrationId.BS_INSIGHT -> "One comparison. Half the array. Gone."

            // ── WATCH walkthrough — one short sentence per line, never a paragraph.
            NarrationId.BS_WATCH_SETUP -> "Let's find ${arg(0)}."
            NarrationId.BS_WATCH_SETUP_SUPPORT ->
                "Binary Search works only on a sorted array."

            NarrationId.BS_WATCH_CHECK_MIDDLE -> "First, check the middle."
            NarrationId.BS_WATCH_CHECK_MIDDLE_SUPPORT ->
                "We don't need to start from the beginning."
            NarrationId.BS_WATCH_CHECK_MIDDLE_AGAIN ->
                "Now check the middle of what is left."
            NarrationId.BS_WATCH_CHECK_MIDDLE_AGAIN_SUPPORT ->
                "${arg(0)} numbers are still in play."

            // Args: span, lo, hi, mid. Named positions, so the learner can check
            // the arithmetic against the lo / hi labels under the array.
            // Args: mid index, value there. Said the first time a middle is
            // miscounted — there is no reasoning route to an index, so the
            // answer and its working arrive together.
            NarrationId.BS_RETRY_MIDDLE_IS ->
                "Not that one. The middle of the live range is position ${arg(0)}, " +
                    "which holds ${arg(1)}."

            NarrationId.BS_WATCH_MID_FORMULA ->
                "${arg(0)} positions are in play — left is ${arg(1)}, right is ${arg(2)}. " +
                    "The one in the middle is ${arg(3)}."
            // Never "we take the left one" — `left` is a pointer on screen, and
            // the sentence would read as naming it rather than describing which
            // of the two centre positions wins.
            NarrationId.BS_WATCH_MID_FORMULA_ROUNDED ->
                "${arg(0)} positions are in play — left is ${arg(1)}, right is ${arg(2)}. " +
                    "An even count has no exact middle, so the division rounds " +
                    "down to ${arg(3)}."

            NarrationId.BS_WATCH_COMPARE_LESS -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.BS_WATCH_COMPARE_GREATER -> "${arg(0)} is larger than ${arg(1)}."
            NarrationId.BS_WATCH_COMPARE_EQUAL -> "${arg(0)} is exactly ${arg(1)}."

            NarrationId.BS_WATCH_MUST_BE_LEFT -> "So the target must be to the left."
            NarrationId.BS_WATCH_MUST_BE_RIGHT -> "So the target must be to the right."
            NarrationId.BS_WATCH_MUST_BE_RIGHT_TARGET -> "${arg(0)} must be on the right."
            NarrationId.BS_WATCH_IGNORE_LEFT ->
                "We can safely ignore everything on the left — ${arg(0)} numbers gone."
            NarrationId.BS_WATCH_IGNORE_RIGHT ->
                "We can safely ignore everything on the right — ${arg(0)} numbers gone."
            NarrationId.BS_WATCH_RANGE_EMPTY -> "Nothing is left to check."

            NarrationId.BS_WATCH_FOUND -> "Found it!"
            NarrationId.BS_WATCH_FOUND_SUPPORT ->
                "${arg(0)} checks instead of checking all ${arg(1)}."
            NarrationId.BS_WATCH_NOT_FOUND -> "${arg(0)} is not in this array."

            NarrationId.BS_WATCH_INSIGHT -> "One comparison. Half the search space."
            NarrationId.BS_WATCH_INSIGHT_SUPPORT ->
                "Binary Search repeatedly cuts the number of possibilities in half."

            NarrationId.BS_WATCH_SUMMARY_FOUND ->
                "Found ${arg(0)} in ${arg(1)} checks, not ${arg(2)}."
            NarrationId.BS_WATCH_SUMMARY_NOT_FOUND ->
                "${arg(0)} is not here — and it took ${arg(1)} checks to prove it."
            NarrationId.BS_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.BS_IDEA_1 -> "Check the middle."
            NarrationId.BS_IDEA_2 -> "Compare with the target."
            NarrationId.BS_IDEA_3 -> "Eliminate half."
            NarrationId.BS_IDEA_4 -> "Repeat."

            // ── The TRY ladder. Level 1 points, level 2 asks, level 3 tells.
            NarrationId.BS_RETRY_LOOK_AGAIN -> "Look at the comparison again."
            NarrationId.BS_RETRY_ASK_LARGER ->
                "The target is larger than the middle. Which side can still contain it?"
            NarrationId.BS_RETRY_ASK_SMALLER ->
                "The target is smaller than the middle. Which side can still contain it?"
            NarrationId.BS_RETRY_EXPLAIN_RIGHT ->
                "${arg(0)} is smaller than ${arg(1)}, so ${arg(1)} must be on the right. " +
                    "Keep the right half."
            NarrationId.BS_RETRY_EXPLAIN_LEFT ->
                "${arg(0)} is larger than ${arg(1)}, so ${arg(1)} must be on the left. " +
                    "Keep the left half."
            NarrationId.BS_RETRY_EXPLAIN_FOUND ->
                "The middle is already ${arg(1)}. You have found it."

            NarrationId.BS_CORRECT_RIGHT ->
                "${arg(0)} < ${arg(1)}, so ${arg(1)} must be on the right."
            NarrationId.BS_CORRECT_LEFT ->
                "${arg(0)} > ${arg(1)}, so ${arg(1)} must be on the left."
            NarrationId.BS_CORRECT_FOUND -> "${arg(1)} is right here."

            // ── CHALLENGE. Neutral and short: the learner is on their own.
            NarrationId.BS_ASK_WHERE_TO_LOOK -> "Where should you look first?"
            NarrationId.BS_ASK_NEXT_MOVE -> "What is your next move?"
            NarrationId.BS_CORRECT_MIDDLE -> "Checking ${arg(0)}."
            NarrationId.BS_RETRY_NOT_THE_MIDDLE ->
                "That is not the middle of the current range."
            NarrationId.BS_RETRY_MIDDLE_OF_RANGE ->
                "${arg(0)} values are still active. Which one sits in the middle of them?"
            NarrationId.BS_RETRY_MIDDLE_EXPLICIT -> "The middle of the active range is ${arg(0)}."
            NarrationId.BS_HINT_START_MIDDLE ->
                "Binary Search starts with the middle of the active range."
            NarrationId.BS_HINT_MIDDLE_OF_ACTIVE ->
                "Count the ${arg(0)} active values and take the centre one."

            // ── Bubble Sort. The decision is swap-or-keep, and only that.
            NarrationId.BUBBLE_ASK_WHICH_ACTION -> "Which action should we take?"
            NarrationId.BUBBLE_ASK_WHAT_HAPPENS -> "What should happen here?"
            NarrationId.BUBBLE_ASK_YOUR_MOVE -> "Your move."
            NarrationId.BUBBLE_OPTION_SWAP -> "SWAP"
            NarrationId.BUBBLE_OPTION_KEEP -> "KEEP"
            NarrationId.BUBBLE_COMPARE_GREATER -> "${arg(0)} > ${arg(1)}"
            NarrationId.BUBBLE_COMPARE_LESS -> "${arg(0)} < ${arg(1)}"
            NarrationId.BUBBLE_SWAPPED -> "${arg(0)} is larger than ${arg(1)}, so swap them."
            NarrationId.BUBBLE_KEPT -> "${arg(0)} is already smaller than ${arg(1)}."
            NarrationId.BUBBLE_PASS_COMPLETE -> "Pass ${arg(1)} complete. ${arg(0)} is in place."
            NarrationId.BUBBLE_NO_SWAPS -> "A full pass with no swaps."
            NarrationId.BUBBLE_SORTED -> "Sorted."

            NarrationId.BUBBLE_HINT -> "Is ${arg(0)} larger than ${arg(1)}?"
            NarrationId.BUBBLE_HINT_LOOK -> "Compare the two highlighted numbers."
            NarrationId.BUBBLE_HINT_RULE -> "In Bubble Sort, larger values move to the right."
            NarrationId.BUBBLE_RETRY_LOOK_AGAIN -> "Look at the two numbers again."
            NarrationId.BUBBLE_RETRY_ASK_ORDER ->
                "Is ${arg(0)} on the correct side of ${arg(1)}?"
            NarrationId.BUBBLE_RETRY_EXPLAIN_SWAP ->
                "${arg(0)} is larger than ${arg(1)}. Larger values move right, so swap them."
            NarrationId.BUBBLE_RETRY_EXPLAIN_KEEP ->
                "${arg(0)} is already smaller than ${arg(1)}. Leave them where they are."
            NarrationId.BUBBLE_WHY_SWAP_WRONG ->
                "Swapping would put the larger value on the left."
            NarrationId.BUBBLE_WHY_KEEP_WRONG ->
                "Keeping them leaves the larger value on the left."

            // ── Bubble Sort WATCH.
            NarrationId.BUBBLE_WATCH_SETUP -> "Let's sort these from smallest to largest."
            NarrationId.BUBBLE_WATCH_SETUP_SUPPORT ->
                "Bubble Sort only ever looks at two neighbours at a time."
            NarrationId.BUBBLE_WATCH_COMPARE_NEIGHBOURS -> "Compare neighbouring numbers."
            NarrationId.BUBBLE_WATCH_NEIGHBOURS_SUPPORT ->
                "Start with the first two."
            NarrationId.BUBBLE_WATCH_COMPARE_NEXT -> "Now compare the next pair."
            NarrationId.BUBBLE_WATCH_LARGER -> "${arg(0)} is larger than ${arg(1)}."
            NarrationId.BUBBLE_WATCH_SMALLER -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.BUBBLE_WATCH_SWAP -> "They are in the wrong order, so swap them."
            NarrationId.BUBBLE_WATCH_KEEP -> "They are already in the correct order."
            NarrationId.BUBBLE_WATCH_KEEP_SUPPORT -> "Keep them where they are."
            NarrationId.BUBBLE_WATCH_REACHED_END -> "${arg(0)} has reached the end."
            NarrationId.BUBBLE_WATCH_PASS_N -> "Pass ${arg(1)} complete."
            NarrationId.BUBBLE_WATCH_PASS_SUPPORT ->
                "After a full pass, the largest unsorted number is in its final position."
            NarrationId.BUBBLE_WATCH_NO_SWAPS -> "A full pass, and nothing moved."
            NarrationId.BUBBLE_WATCH_NO_SWAPS_SUPPORT ->
                "No swaps means the array is already sorted. We can stop."
            NarrationId.BUBBLE_WATCH_SORTED -> "Sorted."
            NarrationId.BUBBLE_WATCH_SORTED_SUPPORT -> "Every value is in its final position."
            NarrationId.BUBBLE_WATCH_INSIGHT -> "The largest number bubbles to the end."
            NarrationId.BUBBLE_WATCH_INSIGHT_SUPPORT ->
                "Compare neighbours. Swap when they are out of order. Repeat."
            NarrationId.BUBBLE_WATCH_SUMMARY ->
                "Sorted in ${arg(0)} comparisons and ${arg(1)} swaps."
            NarrationId.BUBBLE_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.BUBBLE_PREDICT_PROMPT -> "${arg(0)} and ${arg(1)} — what should happen?"
            NarrationId.BUBBLE_PREDICT_SUPPORT -> "Nothing is scored here. Just commit to an answer."
            NarrationId.BUBBLE_IDEA_1 -> "Compare neighbours."
            NarrationId.BUBBLE_IDEA_2 -> "Swap when they are out of order."
            NarrationId.BUBBLE_IDEA_3 -> "Repeat until a pass makes no swaps."

            // ── Selection Sort. Judge against a remembered value, then place it.
            NarrationId.SELECT_ASK_IS_SMALLER -> "Is ${arg(0)} the new smallest?"
            NarrationId.SELECT_ASK_STILL_SMALLEST -> "What about ${arg(0)}?"
            NarrationId.SELECT_ASK_YOUR_TURN -> "Your turn."
            NarrationId.SELECT_ASK_WHERE -> "Where should ${arg(0)} go?"
            NarrationId.SELECT_OPTION_NEW_MIN -> "NEW MIN"
            NarrationId.SELECT_OPTION_NOT_SMALLER -> "NOT SMALLER"
            NarrationId.SELECT_OPTION_PLACE -> "Place here"
            NarrationId.SELECT_COMPARE_LESS -> "${arg(0)} < ${arg(1)}"
            NarrationId.SELECT_COMPARE_MORE -> "${arg(0)} > ${arg(1)}"
            NarrationId.SELECT_NEW_MIN -> "${arg(0)} is smaller than ${arg(1)}. New smallest."
            NarrationId.SELECT_KEPT_MIN -> "${arg(0)} is not smaller than ${arg(1)}."
            NarrationId.SELECT_PLACED -> "${arg(0)} is in its final position."
            NarrationId.SELECT_SORTED -> "Sorted."
            NarrationId.SELECT_VALUE -> "${arg(0)}"

            NarrationId.SELECT_HINT_COMPARE ->
                "Compare ${arg(0)} with the smallest you have found so far."
            NarrationId.SELECT_HINT_RULE ->
                "Selection Sort only remembers the smallest value it has seen."
            NarrationId.SELECT_HINT_PLACE ->
                "The minimum goes at the front of the unsorted portion."
            NarrationId.SELECT_RETRY_LOOK -> "Look at ${arg(0)} and ${arg(1)} again."
            NarrationId.SELECT_RETRY_ASK -> "Is ${arg(0)} smaller than ${arg(1)}?"
            NarrationId.SELECT_RETRY_EXPLAIN_SMALLER ->
                "${arg(0)} is smaller than ${arg(1)}, so it becomes the new minimum."
            NarrationId.SELECT_RETRY_EXPLAIN_LARGER ->
                "${arg(0)} is larger than ${arg(1)}, so the minimum does not change."
            NarrationId.SELECT_RETRY_PLACE_LOOK -> "That is not the front of the unsorted part."
            NarrationId.SELECT_RETRY_PLACE_ASK ->
                "Which slot is the first one that is not already sorted?"
            NarrationId.SELECT_RETRY_PLACE_EXPLAIN ->
                "${arg(0)} belongs at the very front of the unsorted portion."
            NarrationId.SELECT_WHY_MISSED_SMALLER ->
                "You would keep ${arg(1)}, but ${arg(0)} is smaller than it."
            NarrationId.SELECT_WHY_NOT_SMALLER ->
                "${arg(0)} is larger than ${arg(1)}, so it cannot be the minimum."

            // ── Selection Sort WATCH.
            NarrationId.SELECT_WATCH_SETUP -> "Let's sort these from smallest to largest."
            NarrationId.SELECT_WATCH_SETUP_SUPPORT ->
                "Selection Sort repeatedly finds the smallest number that remains."
            NarrationId.SELECT_WATCH_FIRST_SLOT -> "The first position needs the smallest number."
            NarrationId.SELECT_WATCH_FIRST_CANDIDATE ->
                "Start by assuming it is ${arg(0)}, then look for something smaller."
            NarrationId.SELECT_WATCH_SMALLER -> "${arg(0)} is smaller than ${arg(1)}."
            NarrationId.SELECT_WATCH_NOT_SMALLER ->
                "${arg(0)} is not smaller than ${arg(1)}."
            NarrationId.SELECT_WATCH_NEW_MIN -> "So ${arg(0)} becomes our new minimum."
            NarrationId.SELECT_WATCH_PLACE ->
                "The scan is done. ${arg(0)} is the smallest."
            NarrationId.SELECT_WATCH_PLACE_N -> "Pass ${arg(1)}: ${arg(0)} goes to the front."
            NarrationId.SELECT_WATCH_GROWS -> "The sorted portion grows by one."
            NarrationId.SELECT_WATCH_SORTED -> "Sorted."
            NarrationId.SELECT_WATCH_SORTED_SUPPORT ->
                "Every value has been selected and placed."
            NarrationId.SELECT_WATCH_INSIGHT -> "The sorted portion grows one at a time."
            NarrationId.SELECT_WATCH_INSIGHT_SUPPORT ->
                "Find the minimum. Place it at the front. Repeat."
            NarrationId.SELECT_WATCH_SUMMARY -> "Sorted in ${arg(0)} comparisons."
            NarrationId.SELECT_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.SELECT_PREDICT_PROMPT -> "Which number goes first?"
            NarrationId.SELECT_PREDICT_SUPPORT -> "Nothing is scored here."
            NarrationId.SELECT_PREDICT_RIGHT ->
                "Exactly — ${arg(0)} is the smallest value left."
            NarrationId.SELECT_PREDICT_WRONG ->
                "Look through the unsorted portion: ${arg(0)} is the smallest value left."
            NarrationId.SELECT_IDEA_1 -> "Find the smallest remaining value."
            NarrationId.SELECT_IDEA_2 -> "Place it at the front of the unsorted portion."
            NarrationId.SELECT_IDEA_3 -> "Repeat until nothing is left."

            // ── Insertion Sort. Shift larger values right, then drop the key in.
            NarrationId.INSERT_ASK_WHAT_HAPPENS -> "What should happen to ${arg(0)}?"
            NarrationId.INSERT_ASK_NEXT_MOVE -> "What is your next move?"
            NarrationId.INSERT_ASK_YOUR_MOVE -> "Your move."
            NarrationId.INSERT_ASK_NOTHING_LEFT -> "Nothing to the left. Now what?"
            NarrationId.INSERT_OPTION_SHIFT -> "MOVE RIGHT"
            NarrationId.INSERT_OPTION_INSERT -> "INSERT KEY"
            NarrationId.INSERT_COMPARE_LARGER -> "${arg(0)} > ${arg(1)}"
            NarrationId.INSERT_COMPARE_SMALLER -> "${arg(0)} < ${arg(1)}"
            NarrationId.INSERT_NOTHING_LEFT -> "There is nothing to the left of the gap."
            NarrationId.INSERT_KEY_TAKEN -> "${arg(0)} is the key."
            NarrationId.INSERT_SHIFTED -> "${arg(0)} is larger than ${arg(1)}, so it moves right."
            NarrationId.INSERT_INSERTED -> "${arg(0)} is not larger, so ${arg(1)} goes here."
            NarrationId.INSERT_INSERTED_FRONT -> "Nothing larger remains. ${arg(1)} goes here."
            NarrationId.INSERT_SORTED -> "Sorted."

            NarrationId.INSERT_HINT_COMPARE -> "Compare ${arg(0)} with the key, ${arg(1)}."
            NarrationId.INSERT_HINT_RULE ->
                "Anything larger than the key has to move right to make room."
            NarrationId.INSERT_RETRY_LOOK -> "Look at ${arg(0)} and the key again."
            NarrationId.INSERT_RETRY_ASK -> "Is ${arg(0)} larger than ${arg(1)}?"
            NarrationId.INSERT_RETRY_EXPLAIN_SHIFT ->
                "${arg(0)} is larger than ${arg(1)}, so move it right to make space."
            NarrationId.INSERT_RETRY_EXPLAIN_INSERT ->
                "${arg(0)} is not larger than ${arg(1)}, so the key belongs right here."
            NarrationId.INSERT_RETRY_EXPLAIN_FRONT ->
                "There is nothing left to compare. The key belongs at the front."
            NarrationId.INSERT_WHY_TOO_EARLY ->
                "Inserting now would leave ${arg(0)} on the wrong side of the key."
            NarrationId.INSERT_WHY_NO_SHIFT ->
                "Moving ${arg(0)} right would push a smaller value out of place."

            // ── Insertion Sort WATCH.
            NarrationId.INSERT_WATCH_SETUP ->
                "We build the sorted portion one value at a time."
            NarrationId.INSERT_WATCH_SETUP_SUPPORT ->
                "${arg(0)} is on its own, so it already counts as sorted."
            NarrationId.INSERT_WATCH_TAKE_KEY -> "Take ${arg(0)} as the key."
            NarrationId.INSERT_WATCH_KEY_SUPPORT ->
                "Lift it out. That leaves a gap we can shift values into."
            NarrationId.INSERT_WATCH_LARGER -> "${arg(0)} is larger than ${arg(1)}."
            NarrationId.INSERT_WATCH_SHIFT ->
                "Shift ${arg(0)} one place right. Nothing is swapped — the gap moves left."
            NarrationId.INSERT_WATCH_NOTHING_LARGER ->
                "Nothing larger remains. Insert ${arg(0)} here."
            NarrationId.INSERT_WATCH_ALREADY_PLACED ->
                "${arg(0)} is already in the right place."
            NarrationId.INSERT_WATCH_INSERTED_N -> "${arg(0)} is inserted."
            NarrationId.INSERT_WATCH_GROWS -> "The sorted portion grows by one."
            NarrationId.INSERT_WATCH_SORTED -> "Sorted."
            NarrationId.INSERT_WATCH_SORTED_SUPPORT -> "The left side was sorted the whole way."
            NarrationId.INSERT_WATCH_INSIGHT -> "The left side is always sorted."
            NarrationId.INSERT_WATCH_INSIGHT_SUPPORT ->
                "Pick the next value. Shift larger values right. Insert it into place."
            NarrationId.INSERT_WATCH_SUMMARY -> "Sorted in ${arg(0)} insertions."
            NarrationId.INSERT_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.INSERT_PREDICT_PROMPT -> "The next key is ${arg(1)}. What happens to ${arg(0)}?"
            NarrationId.INSERT_PREDICT_SUPPORT -> "Nothing is scored here."
            NarrationId.INSERT_PREDICT_RIGHT_SHIFT ->
                "Exactly — ${arg(0)} is larger than ${arg(1)}, so it moves right."
            NarrationId.INSERT_PREDICT_RIGHT_KEEP ->
                "Exactly — ${arg(0)} is not larger than ${arg(1)}, so nothing moves."
            NarrationId.INSERT_IDEA_1 -> "Pick the next value as the key."
            NarrationId.INSERT_IDEA_2 -> "Shift larger values right."
            NarrationId.INSERT_IDEA_3 -> "Insert the key into the gap."

            // ── Merge Sort. Divide, then repeatedly take the smaller front value.
            NarrationId.MERGE_ASK_SPLIT -> "Where should we split this array?"
            NarrationId.MERGE_ASK_FIRST -> "Which value should come first?"
            NarrationId.MERGE_ASK_NEXT -> "Which value comes next?"
            NarrationId.MERGE_ASK_YOUR_MOVE -> "Your move."
            NarrationId.MERGE_OPTION_SPLIT -> "Split here"
            NarrationId.MERGE_VALUE -> "${arg(0)}"
            NarrationId.MERGE_COMPARE -> "${arg(0)} and ${arg(1)}"
            NarrationId.MERGE_TOOK -> "${arg(0)} is smaller than ${arg(1)}, so it goes first."
            NarrationId.MERGE_TOOK_VALUE -> "${arg(0)} is placed."
            NarrationId.MERGE_SPLIT_DONE -> "Split into two halves."
            NarrationId.MERGE_DIVIDE_AGAIN -> "Split each half again."
            NarrationId.MERGE_DIVIDED_TO_ONE -> "Every piece now holds one value."
            NarrationId.MERGE_BASE_CASE -> "A single value is already sorted."
            NarrationId.MERGE_NEXT_PAIR -> "Now merge the next pair."
            NarrationId.MERGE_LEVEL_DONE -> "Merging into runs of ${arg(0)}."
            NarrationId.MERGE_SORTED -> "Sorted."

            NarrationId.MERGE_HINT_SPLIT -> "Merge Sort always splits down the middle."
            NarrationId.MERGE_HINT_FRONTS ->
                "Only the front value of each half can come next: ${arg(0)} or ${arg(1)}."
            NarrationId.MERGE_HINT_RULE -> "Always take the smaller of the two front values."
            NarrationId.MERGE_RETRY_SPLIT_LOOK -> "That is not the halfway point."
            NarrationId.MERGE_RETRY_SPLIT_ASK ->
                "There are ${arg(0)} values. Where is the middle?"
            NarrationId.MERGE_RETRY_SPLIT_EXPLAIN -> "The two halves meet at position ${arg(0)}."
            NarrationId.MERGE_RETRY_LOOK -> "Look at the front of each half again."
            NarrationId.MERGE_RETRY_ASK -> "Which is smaller, ${arg(0)} or ${arg(1)}?"
            NarrationId.MERGE_RETRY_EXPLAIN ->
                "${arg(0)} is smaller than ${arg(1)}, so ${arg(0)} comes next."
            NarrationId.MERGE_WHY_WRONG ->
                "${arg(0)} cannot come before ${arg(1)} — a smaller value is still waiting."

            // ── Merge Sort WATCH.
            NarrationId.MERGE_WATCH_SETUP ->
                "Sorting a big array is easier if we break it up."
            NarrationId.MERGE_WATCH_SETUP_SUPPORT ->
                "Merge Sort divides first, then combines the pieces back in order."
            NarrationId.MERGE_WATCH_FIRST_SPLIT -> "Split the array into two halves."
            NarrationId.MERGE_WATCH_SPLIT_AGAIN -> "Split each half again."
            NarrationId.MERGE_WATCH_DIVIDED -> "Keep dividing until each piece holds one value."
            NarrationId.MERGE_WATCH_KEEP_DIVIDING -> "Nothing has moved yet — only the boundaries."
            NarrationId.MERGE_WATCH_BASE_CASE -> "A single value is already sorted."
            NarrationId.MERGE_WATCH_START_MERGE -> "Now combine the pieces back together."
            NarrationId.MERGE_WATCH_MERGE_RULE ->
                "Compare the front value of each piece and take the smaller one."
            NarrationId.MERGE_WATCH_COMPARE_FRONTS ->
                "Only the fronts matter — everything behind them is already in order."
            NarrationId.MERGE_WATCH_TOOK -> "${arg(0)} is smaller than ${arg(1)}, so take ${arg(0)}."
            NarrationId.MERGE_WATCH_ONLY_LEFT -> "${arg(0)} is all that is left, so it goes next."
            NarrationId.MERGE_WATCH_LEVEL_DONE -> "Now we have sorted runs of ${arg(0)}."
            NarrationId.MERGE_WATCH_LEVEL_SUPPORT -> "Merge those, and the runs double again."
            NarrationId.MERGE_WATCH_SORTED -> "Sorted."
            NarrationId.MERGE_WATCH_SORTED_SUPPORT ->
                "The last merge combined the two sorted halves into one."
            NarrationId.MERGE_WATCH_INSIGHT -> "Divide. Sort. Merge."
            NarrationId.MERGE_WATCH_INSIGHT_SUPPORT ->
                "Merge Sort never sorts everything at once. It combines smaller sorted pieces."
            NarrationId.MERGE_WATCH_SUMMARY -> "Sorted in ${arg(0)} comparisons."
            NarrationId.MERGE_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.MERGE_PREDICT_PROMPT -> "Which value should be placed next?"
            NarrationId.MERGE_PREDICT_SUPPORT -> "Nothing is scored here."
            NarrationId.MERGE_PREDICT_RIGHT -> "Exactly — ${arg(0)} is smaller than ${arg(1)}."
            NarrationId.MERGE_PREDICT_WRONG ->
                "Look at the front of each half: ${arg(0)} is smaller than ${arg(1)}."
            NarrationId.MERGE_IDEA_1 -> "Divide until each piece holds one value."
            NarrationId.MERGE_IDEA_2 -> "A single value is already sorted."
            NarrationId.MERGE_IDEA_3 -> "Merge by taking the smaller front value."

            // ── Quick Sort. Every value is measured against one pivot.
            NarrationId.QUICK_ASK_COMPARE -> "Is ${arg(0)} smaller or larger than the pivot?"
            NarrationId.QUICK_ASK_WHICH_SIDE -> "Which side does ${arg(0)} belong on?"
            NarrationId.QUICK_ASK_YOUR_MOVE -> "Your move."
            NarrationId.QUICK_ASK_PIVOT_HOME -> "Where does the pivot ${arg(0)} belong?"
            NarrationId.QUICK_OPTION_LEFT -> "LEFT"
            NarrationId.QUICK_OPTION_RIGHT -> "RIGHT"
            NarrationId.QUICK_OPTION_PLACE -> "Place here"
            NarrationId.QUICK_COMPARE_LESS -> "${arg(0)} ≤ ${arg(1)}"
            NarrationId.QUICK_COMPARE_MORE -> "${arg(0)} > ${arg(1)}"
            NarrationId.QUICK_PIVOT_IS -> "${arg(0)} is the pivot."
            NarrationId.QUICK_WENT_LEFT ->
                "${arg(0)} is not larger than ${arg(1)}, so it goes left."
            NarrationId.QUICK_WENT_RIGHT ->
                "${arg(0)} is larger than ${arg(1)}, so it goes right."
            NarrationId.QUICK_PIVOT_FINAL -> "${arg(0)} is now in its final position."
            NarrationId.QUICK_NEXT_PARTITION -> "Now the partition of ${arg(0)}."
            NarrationId.QUICK_SORTED -> "Sorted."

            NarrationId.QUICK_HINT_COMPARE -> "Compare ${arg(0)} with the pivot, ${arg(1)}."
            NarrationId.QUICK_HINT_RULE ->
                "Smaller or equal goes left; larger goes right."
            NarrationId.QUICK_HINT_PIVOT_HOME ->
                "The pivot belongs between the two groups."
            NarrationId.QUICK_RETRY_LOOK -> "Look at ${arg(0)} and the pivot again."
            NarrationId.QUICK_RETRY_ASK -> "Is ${arg(0)} larger than ${arg(1)}?"
            NarrationId.QUICK_RETRY_EXPLAIN_LEFT ->
                "${arg(0)} is not larger than ${arg(1)}, so it belongs on the LEFT."
            NarrationId.QUICK_RETRY_EXPLAIN_RIGHT ->
                "${arg(0)} is larger than ${arg(1)}, so it belongs on the RIGHT."
            NarrationId.QUICK_RETRY_PLACE_LOOK -> "That is not where the two groups meet."
            NarrationId.QUICK_RETRY_PLACE_ASK ->
                "Everything left of ${arg(0)} is smaller. Where does that end?"
            NarrationId.QUICK_RETRY_PLACE_EXPLAIN ->
                "${arg(0)} goes right after the last smaller value."
            NarrationId.QUICK_WHY_NOT_LEFT ->
                "Putting ${arg(0)} left would leave a larger value below the pivot."
            NarrationId.QUICK_WHY_NOT_RIGHT ->
                "Putting ${arg(0)} right would leave a smaller value above the pivot."

            // ── Quick Sort WATCH.
            NarrationId.QUICK_WATCH_SETUP -> "Quick Sort starts by choosing a pivot."
            NarrationId.QUICK_WATCH_SETUP_SUPPORT ->
                "The pivot splits the array into a smaller side and a larger side."
            NarrationId.QUICK_WATCH_PIVOT -> "${arg(0)} is our pivot."
            NarrationId.QUICK_WATCH_PIVOT_SUPPORT ->
                "We compare every other value with it."
            NarrationId.QUICK_WATCH_SMALLER -> "${arg(0)} is not larger than ${arg(1)}."
            NarrationId.QUICK_WATCH_LARGER -> "${arg(0)} is larger than ${arg(1)}."
            NarrationId.QUICK_WATCH_BELONGS_LEFT -> "It belongs on the left side."
            NarrationId.QUICK_WATCH_BELONGS_RIGHT -> "It belongs on the right side."
            NarrationId.QUICK_WATCH_PARTITIONED ->
                "Everything left of ${arg(0)} is smaller, everything right is larger."
            NarrationId.QUICK_WATCH_PIVOT_FINAL ->
                "The pivot is now in its final position — it never moves again."
            NarrationId.QUICK_WATCH_PIVOT_PLACED -> "${arg(0)} has found its place."
            NarrationId.QUICK_WATCH_REPEAT ->
                "Now the same idea again, on a smaller partition."
            NarrationId.QUICK_WATCH_SORTED -> "Sorted."
            NarrationId.QUICK_WATCH_SORTED_SUPPORT ->
                "Every pivot found its place, and the leftovers were single values."
            NarrationId.QUICK_WATCH_INSIGHT -> "Every pivot ends up in its final position."
            NarrationId.QUICK_WATCH_INSIGHT_SUPPORT ->
                "Quick Sort never sorts a side completely. It partitions around pivots."
            NarrationId.QUICK_WATCH_SUMMARY -> "${arg(0)} values, each placed by a partition."
            NarrationId.QUICK_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.QUICK_PREDICT_PROMPT -> "${arg(0)} against the pivot ${arg(1)} — which side?"
            NarrationId.QUICK_PREDICT_SUPPORT -> "Nothing is scored here."
            NarrationId.QUICK_IDEA_1 -> "Pick a pivot."
            NarrationId.QUICK_IDEA_2 -> "Partition around it."
            NarrationId.QUICK_IDEA_3 -> "Repeat on each side."

            // ── Counting Sort. Every line names the value and its bucket; the
            //    word "index" never appears, because the offset into the table is
            //    the app's business and the value is the learner's.
            NarrationId.CS_OPTION_BUCKET -> "Bucket ${arg(0)}"
            NarrationId.CS_ASK_BUCKET -> "Which bucket counts ${arg(0)}?"
            NarrationId.CS_ASK_NEXT_OUT -> "Which value goes in slot ${arg(0)}?"
            NarrationId.CS_COUNTED ->
                "${arg(0)} counted. count[${arg(1)}]: ${arg(2)} → ${arg(3)}"
            NarrationId.CS_PLACED ->
                "${arg(0)} placed in slot ${arg(1)}. ${arg(2)} left in its bucket."
            NarrationId.CS_HINT_BUCKET ->
                "A bucket counts one value, and it has that value written under it."
            NarrationId.CS_HINT_REBUILD ->
                "Read the table left to right. The smallest value that still has " +
                    "something in it comes out next."
            NarrationId.CS_RETRY_COUNT_LOOK -> "Look at the value again: it is ${arg(0)}."
            NarrationId.CS_RETRY_COUNT_ASK ->
                "Counting a ${arg(0)} changes how many ${arg(0)}s we have seen. " +
                    "Which bucket holds that number?"
            NarrationId.CS_RETRY_COUNT_EXPLAIN ->
                "A ${arg(0)} is counted in bucket ${arg(0)}, which goes from " +
                    "${arg(1)} to ${arg(2)}. Every other bucket counts a different value."
            NarrationId.CS_RETRY_PLACE_LOOK ->
                "The output is built smallest first. Read the table from the left."
            NarrationId.CS_RETRY_PLACE_ASK ->
                "Which is the smallest value that still has something left to give?"
            NarrationId.CS_RETRY_PLACE_EXPLAIN ->
                "${arg(0)} is the smallest value with anything left — " +
                    "${plural(1, "there is 1", "there are ${arg(1)}")} still to place. " +
                    "It comes out next."
            NarrationId.CS_WHY_WRONG_BUCKET ->
                "Bucket ${arg(0)} counts how many ${arg(0)}s there are. This value is ${arg(1)}."
            NarrationId.CS_WHY_EMPTY_BUCKET ->
                "${arg(0)} was never counted — its bucket is 0, so no ${arg(0)} goes " +
                    "in the output at all."
            NarrationId.CS_WHY_BUCKET_SPENT ->
                "Every ${arg(0)} is already placed. That bucket has nothing left to give."
            NarrationId.CS_WHY_OUT_OF_ORDER ->
                "${arg(0)} is larger than ${arg(1)}, and ${arg(1)} still has values " +
                    "waiting. Taking ${arg(0)} now would put it in front of them."
            NarrationId.CS_CORRECT_COUNT ->
                "Yes — count[${arg(0)}] goes from ${arg(1)} to ${arg(2)}."
            NarrationId.CS_CORRECT_PLACE ->
                "Yes — ${arg(0)} comes out next, and its bucket has ${arg(1)} left."

            // ── Counting Sort — WATCH
            NarrationId.CS_WATCH_SETUP -> "Sort this array without comparing anything."
            NarrationId.CS_WATCH_SETUP_SUPPORT ->
                "Bubble, Selection and Quick Sort all decide order by comparing " +
                    "elements. This one never compares two values at all."
            NarrationId.CS_WATCH_RANGE -> "The values run from ${arg(0)} to ${arg(1)}."
            NarrationId.CS_WATCH_RANGE_SUPPORT ->
                "That range decides the size of the table: ${arg(0)} buckets, one " +
                    "for every value from ${arg(1)} to ${arg(2)}."
            NarrationId.CS_WATCH_TABLE -> "A count for each of the ${arg(0)} values, all starting at 0."
            NarrationId.CS_WATCH_TABLE_SUPPORT ->
                "Nothing has been counted yet, so every bucket is empty."
            NarrationId.CS_WATCH_COUNT ->
                "${arg(0)} goes in bucket ${arg(0)}: ${arg(1)} → ${arg(2)}."
            NarrationId.CS_WATCH_COUNT_FIRST -> "The first ${arg(0)} we have seen."
            NarrationId.CS_WATCH_COUNT_AGAIN ->
                "That is ${arg(1)} of them. A bucket counts, it does not just remember."
            NarrationId.CS_WATCH_COUNT_REST -> "The last ${arg(0)} values are counted the same way."
            NarrationId.CS_WATCH_COUNT_REST_SUPPORT ->
                "One pass over the array, one bucket raised each time."
            NarrationId.CS_WATCH_COUNTED -> "The table now says how many of each value there are."
            NarrationId.CS_WATCH_COUNTED_SUPPORT ->
                "All ${arg(0)} values, in one pass, and the array is not needed again."
            NarrationId.CS_WATCH_PLACE ->
                "Bucket ${arg(0)} holds ${arg(1)} — place a ${arg(0)}."
            NarrationId.CS_WATCH_PLACE_MORE -> "${arg(1)} more ${arg(0)} still to place."
            NarrationId.CS_WATCH_PLACE_LAST -> "That bucket is empty now. Move to the next value."
            NarrationId.CS_WATCH_PLACE_REST -> "The rest of the table is read the same way."
            NarrationId.CS_WATCH_PLACE_REST_SUPPORT ->
                "Left to right, each bucket giving up as many values as it counted — " +
                    "and empty buckets giving up none."
            NarrationId.CS_WATCH_DONE -> "Sorted."
            NarrationId.CS_WATCH_DONE_SUPPORT ->
                "The order came out of the table, not out of comparing anything."
            NarrationId.CS_WATCH_INSIGHT ->
                "Count how many times each value appears, then rebuild from the counts."
            NarrationId.CS_WATCH_INSIGHT_SUPPORT ->
                "${arg(0)} comparisons. The table did the ordering, because a bucket's " +
                    "position already is its value."
            NarrationId.CS_WATCH_SUMMARY ->
                "${arg(0)} values, ${arg(1)} buckets, one pass each way."
            NarrationId.CS_WATCH_SUMMARY_SUPPORT ->
                "O(n + k): the count of elements plus the width of the range."
            NarrationId.CS_IDEA_1 -> "Comparison sorts ask which of two values is bigger."
            NarrationId.CS_IDEA_2 -> "Counting Sort asks how many of each value there are."
            NarrationId.CS_IDEA_3 -> "A count of 0 places nothing — that is how gaps are skipped."
            NarrationId.CS_IDEA_4 ->
                "Fast when the range is small, and wasteful when it is not: a table " +
                    "of a million buckets to sort seven values costs a million steps to read."

            // ── Linear structures. Shared wording, so the only thing that reads
            //    differently between Stack and Queue is the rule itself.
            NarrationId.STRUCT_OPTION_POINT -> "This one"
            NarrationId.STRUCT_VALUE -> arg(0)
            NarrationId.STRUCT_PREDICT_RIGHT -> "${arg(0)} is next."
            NarrationId.STRUCT_FULL -> "It is full — ${arg(0)} could not go in."

            // ── Stack. Instructions name the goal; the learner picks the operation.
            NarrationId.STACK_OP_PUSH -> "PUSH"
            NarrationId.STACK_OP_POP -> "POP"
            NarrationId.STACK_OP_PEEK -> "PEEK"
            NarrationId.STACK_ASK_ADD -> "Put ${arg(0)} on the stack."
            NarrationId.STACK_ASK_REMOVE -> "Take the next item off."
            NarrationId.STACK_ASK_PEEK -> "Read the next item without taking it."
            NarrationId.STACK_ASK_PREDICT -> "Which item comes off next?"
            NarrationId.STACK_PUSHED -> "${arg(0)} is now on top."
            NarrationId.STACK_POPPED -> "${arg(0)} came off the top."
            NarrationId.STACK_PEEKED -> "The top is ${arg(0)}. Nothing moved."
            NarrationId.STACK_EMPTY -> "The stack is empty — there is nothing to take."

            NarrationId.STACK_HINT_TOP -> "A stack only lets you touch the top."
            NarrationId.STACK_HINT_RULE -> "Last in, first out."
            NarrationId.STACK_RETRY_LOOK -> "Read the instruction again."
            NarrationId.STACK_RETRY_ASK ->
                "Should the stack grow, shrink, or stay exactly as it is?"
            NarrationId.STACK_RETRY_EXPLAIN_PUSH ->
                "Adding ${arg(0)} means pushing it onto the top."
            NarrationId.STACK_RETRY_EXPLAIN_POP -> "Taking the top item off is POP."
            NarrationId.STACK_RETRY_EXPLAIN_PEEK -> "Reading without removing is PEEK."
            NarrationId.STACK_WHY_PUSH ->
                "PUSH adds an item, and nothing was asked to go in."
            NarrationId.STACK_WHY_POP ->
                "POP removes the top — this step should not shrink the stack."
            NarrationId.STACK_WHY_PEEK ->
                "PEEK only reads. The stack would look exactly the same afterwards."
            NarrationId.STACK_PREDICT_LOOK -> "Look at which item went in last."
            NarrationId.STACK_PREDICT_ASK -> "It is a pile. Which one can you lift off?"
            NarrationId.STACK_PREDICT_EXPLAIN ->
                "The last item pushed sits on top, and the top is the only one you can take."

            // ── Stack WATCH.
            NarrationId.STACK_WATCH_SETUP ->
                "A stack is a pile you can only touch from the top."
            NarrationId.STACK_WATCH_SETUP_SUPPORT -> "Watch where each new item lands."
            NarrationId.STACK_WATCH_PUSH -> "Push ${arg(0)}."
            NarrationId.STACK_WATCH_PUSH_SUPPORT -> "New items always go on top."
            NarrationId.STACK_WATCH_POP -> "Pop takes ${arg(0)}."
            NarrationId.STACK_WATCH_POP_SUPPORT -> "The most recent item leaves first."
            NarrationId.STACK_WATCH_PEEK -> "Peek reads ${arg(0)}."
            NarrationId.STACK_WATCH_PEEK_SUPPORT -> "Nothing was removed."
            NarrationId.STACK_PREDICT_PROMPT -> "Which item comes off next?"
            NarrationId.STACK_PREDICT_RIGHT ->
                "Yes — ${arg(0)} went in last, so it comes out first."
            NarrationId.STACK_PREDICT_WRONG ->
                "Not quite. ${arg(0)} went in last, so it is the one on top."
            NarrationId.STACK_WATCH_INSIGHT -> "Last in, first out."
            NarrationId.STACK_WATCH_INSIGHT_SUPPORT ->
                "A stack hands things back in the reverse of the order they arrived."
            NarrationId.STACK_WATCH_SUMMARY -> "Everything came out top-first."
            NarrationId.STACK_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.STACK_IDEA_1 -> "Push adds to the top."
            NarrationId.STACK_IDEA_2 -> "Pop takes from the top."
            NarrationId.STACK_IDEA_3 -> "Peek reads the top and changes nothing."

            // ── Queue. Same instructions, opposite end.
            NarrationId.QUEUE_OP_ENQUEUE -> "ENQUEUE"
            NarrationId.QUEUE_OP_DEQUEUE -> "DEQUEUE"
            NarrationId.QUEUE_OP_PEEK -> "PEEK FRONT"
            NarrationId.QUEUE_ASK_ADD -> "Put ${arg(0)} in the queue."
            NarrationId.QUEUE_ASK_REMOVE -> "Serve the next item."
            NarrationId.QUEUE_ASK_PEEK -> "Read the next item without serving it."
            NarrationId.QUEUE_ASK_PREDICT -> "Which item is served next?"
            NarrationId.QUEUE_ENQUEUED -> "${arg(0)} joined at the rear."
            NarrationId.QUEUE_DEQUEUED -> "${arg(0)} left from the front."
            NarrationId.QUEUE_PEEKED -> "The front is ${arg(0)}. Nothing moved."
            NarrationId.QUEUE_EMPTY -> "The queue is empty — there is nobody to serve."

            NarrationId.QUEUE_HINT_ENDS ->
                "A queue has two ends: you join at one and leave from the other."
            NarrationId.QUEUE_HINT_RULE -> "First in, first out."
            NarrationId.QUEUE_RETRY_LOOK -> "Read the instruction again."
            NarrationId.QUEUE_RETRY_ASK ->
                "Should the queue grow, shrink, or stay exactly as it is?"
            NarrationId.QUEUE_RETRY_EXPLAIN_ENQUEUE ->
                "Adding ${arg(0)} means enqueueing it at the rear."
            NarrationId.QUEUE_RETRY_EXPLAIN_DEQUEUE -> "Serving the front item is DEQUEUE."
            NarrationId.QUEUE_RETRY_EXPLAIN_PEEK ->
                "Reading the front without serving it is PEEK FRONT."
            NarrationId.QUEUE_WHY_ENQUEUE ->
                "ENQUEUE adds at the rear, and nothing was asked to join."
            NarrationId.QUEUE_WHY_DEQUEUE ->
                "DEQUEUE serves the front — this step should not shorten the queue."
            NarrationId.QUEUE_WHY_PEEK ->
                "PEEK FRONT only reads. The queue would look exactly the same afterwards."
            NarrationId.QUEUE_PREDICT_LOOK ->
                "Look at which item has been waiting longest."
            NarrationId.QUEUE_PREDICT_ASK -> "It is a line. Who gets served first?"
            NarrationId.QUEUE_PREDICT_EXPLAIN ->
                "The first item to join is at the front, and the front is always next."

            // ── Queue WATCH.
            NarrationId.QUEUE_WATCH_SETUP ->
                "A queue is a line: you join at the back and leave from the front."
            NarrationId.QUEUE_WATCH_SETUP_SUPPORT -> "Watch which end each item uses."
            NarrationId.QUEUE_WATCH_ENQUEUE -> "Enqueue ${arg(0)}."
            NarrationId.QUEUE_WATCH_ENQUEUE_SUPPORT -> "New items always join the rear."
            NarrationId.QUEUE_WATCH_DEQUEUE -> "Dequeue serves ${arg(0)}."
            NarrationId.QUEUE_WATCH_DEQUEUE_SUPPORT ->
                "The item that waited longest leaves first."
            NarrationId.QUEUE_WATCH_PEEK -> "Peek reads ${arg(0)}."
            NarrationId.QUEUE_WATCH_PEEK_SUPPORT -> "Nobody was served."
            NarrationId.QUEUE_PREDICT_PROMPT -> "Which item is served next?"
            NarrationId.QUEUE_PREDICT_RIGHT ->
                "Yes — ${arg(0)} joined first, so it leaves first."
            NarrationId.QUEUE_PREDICT_WRONG ->
                "Not quite. ${arg(0)} has been waiting longest."
            NarrationId.QUEUE_WATCH_INSIGHT -> "First in, first out."
            NarrationId.QUEUE_WATCH_INSIGHT_SUPPORT ->
                "A queue hands things back in exactly the order they arrived."
            NarrationId.QUEUE_WATCH_SUMMARY -> "Everything came out front-first."
            NarrationId.QUEUE_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.QUEUE_IDEA_1 -> "Enqueue adds at the rear."
            NarrationId.QUEUE_IDEA_2 -> "Dequeue removes from the front."
            NarrationId.QUEUE_IDEA_3 -> "Peek reads the front and changes nothing."

            // ── Linked List. The nouns are HEAD, node, link and NULL, and the copy
            //    never uses any others — a learner who finishes should be able to
            //    read real linked-list code and recognise every word in it.
            NarrationId.LIST_ASK_IS_THIS_IT -> "Is ${arg(0)} the value we are looking for?"
            NarrationId.LIST_OPTION_YES -> "YES"
            NarrationId.LIST_OPTION_NO -> "NO"
            NarrationId.LIST_OPTION_NODE -> arg(0)
            NarrationId.LIST_OPTION_NULL -> "NULL"
            NarrationId.LIST_OPTION_LINK -> ""
            NarrationId.LIST_FOUND -> "Found ${arg(0)}."
            NarrationId.LIST_FOLLOW_NEXT -> "${arg(0)} is not it. Follow its NEXT link."
            NarrationId.LIST_HIT_NULL ->
                "We reached NULL. ${arg(0)} is not in this list."
            NarrationId.LIST_EMPTY -> "The list is empty. There is no node to visit."
            NarrationId.LIST_NOT_PRESENT ->
                "${arg(0)} is not in this list, so there is nothing to unlink."

            NarrationId.LIST_ASK_WHERE_BELONGS -> "Which link does ${arg(0)} belong in?"
            NarrationId.LIST_ASK_WHICH_LINK -> "Which link has to change to remove ${arg(0)}?"
            NarrationId.LIST_ASK_POINTS_TO -> "What should ${arg(0)} point to?"
            NarrationId.LIST_ASK_REPOINT -> "Where should ${arg(0)} point now?"
            NarrationId.LIST_ASK_HEAD_POINTS -> "Where should HEAD point now?"
            NarrationId.LIST_GAP_OPENED -> "${arg(0)} goes in here."
            NarrationId.LIST_LINK_CHOSEN -> "That is the link into ${arg(0)}."
            NarrationId.LIST_LINKED_TO -> "${arg(0)} now points to ${arg(1)}."
            NarrationId.LIST_LINKED_TO_NULL ->
                "${arg(0)} now points to NULL — it is the last node."
            NarrationId.LIST_REPOINTED -> "The chain skips ${arg(0)} and goes straight to ${arg(1)}."
            NarrationId.LIST_REPOINTED_NULL ->
                "${arg(0)} was the last node, so the chain now ends at NULL."
            NarrationId.LIST_INSERTED -> "${arg(0)} is linked in, ahead of ${arg(1)}."
            NarrationId.LIST_INSERTED_AT_END -> "${arg(0)} is linked in at the end."
            NarrationId.LIST_DELETED -> "${arg(0)} is out. Nothing points to it any more."
            NarrationId.LIST_DELETED_LAST -> "${arg(0)} is out, and the list ends sooner."

            // ── The ladder. Level 1 points, level 2 asks, level 3 tells.
            NarrationId.LIST_HINT_COMPARE -> "Compare ${arg(0)} with ${arg(1)}."
            NarrationId.LIST_HINT_WALK ->
                "There is no jumping ahead. You reach a node by following links to it."
            NarrationId.LIST_HINT_ORDER -> "The list is in order. Where does ${arg(0)} fit?"
            NarrationId.LIST_HINT_PREDECESSOR ->
                "Find the arrow that points *at* ${arg(0)}."
            NarrationId.LIST_HINT_INHERIT ->
                "The new node takes over whatever the gap used to point to."
            NarrationId.LIST_HINT_SKIP_OVER -> "The chain has to get past ${arg(0)}."
            NarrationId.LIST_HINT_LINKS_NOT_BOXES ->
                "Changing a linked list means changing links, never moving boxes."

            NarrationId.LIST_RETRY_LOOK -> "Look at this node again."
            NarrationId.LIST_RETRY_ASK_MATCH -> "Is ${arg(0)} the same as ${arg(1)}?"
            NarrationId.LIST_RETRY_EXPLAIN_MATCH ->
                "${arg(0)} is exactly ${arg(1)}. This is the node."
            NarrationId.LIST_RETRY_EXPLAIN_NO_MATCH ->
                "${arg(0)} is not ${arg(1)}, so keep walking."
            NarrationId.LIST_WHY_MISSED_MATCH ->
                "You walked past it — ${arg(0)} is the value you were looking for."
            NarrationId.LIST_WHY_NOT_A_MATCH ->
                "${arg(0)} is not ${arg(1)}. Saying yes here would end the search early."

            NarrationId.LIST_RETRY_GAP_LOOK -> "Look at where ${arg(0)} would sit."
            NarrationId.LIST_RETRY_GAP_ASK ->
                "Which two values should ${arg(0)} end up between?"
            NarrationId.LIST_RETRY_GAP_EXPLAIN ->
                "${arg(0)} belongs after ${arg(1)} and before ${arg(2)}, so that is the link it goes into."
            NarrationId.LIST_RETRY_LINK_ASK -> "Which node currently points at ${arg(0)}?"
            NarrationId.LIST_RETRY_LINK_EXPLAIN ->
                "${arg(1)} is what points at ${arg(0)}, so that is the link that has to change."

            NarrationId.LIST_RETRY_POINT_LOOK -> "Look at what comes after the gap."
            NarrationId.LIST_RETRY_POINT_ASK ->
                "The link you opened used to point somewhere. Where?"
            NarrationId.LIST_RETRY_POINT_EXPLAIN ->
                "${arg(0)} has to point to ${arg(1)}, or the rest of the list is unreachable."
            NarrationId.LIST_RETRY_POINT_EXPLAIN_NULL ->
                "${arg(0)} is last now, so it points to NULL."

            NarrationId.LIST_RETRY_REPOINT_LOOK -> "Look at what comes after ${arg(0)}."
            NarrationId.LIST_RETRY_REPOINT_ASK ->
                "If nothing may point at ${arg(0)}, what should point there instead?"
            NarrationId.LIST_RETRY_REPOINT_EXPLAIN ->
                "Skip over ${arg(0)} and point straight at ${arg(1)}."
            NarrationId.LIST_RETRY_REPOINT_EXPLAIN_NULL ->
                "${arg(0)} was the last node, so the link becomes NULL."
            NarrationId.LIST_WHY_STILL_LINKED ->
                "That still points at ${arg(0)}, so ${arg(0)} would still be in the list."

            // ── Linked List WATCH.
            NarrationId.LIST_WATCH_MEET -> "Meet the Linked List."
            NarrationId.LIST_WATCH_CHAIN -> "It is a chain of nodes."
            NarrationId.LIST_WATCH_NODE ->
                "Each node stores a value and a link to the next node."
            NarrationId.LIST_WATCH_NODE_SUPPORT -> "The dot on the right is the link."
            NarrationId.LIST_WATCH_LINKS_CONNECT -> "The links connect the nodes into a chain."
            NarrationId.LIST_WATCH_HEAD -> "HEAD points to the first node."
            NarrationId.LIST_WATCH_HEAD_SUPPORT -> "We use HEAD to enter the list."
            NarrationId.LIST_WATCH_START_AT_HEAD -> "Start at HEAD."
            NarrationId.LIST_WATCH_FOLLOW -> "Follow the NEXT link to move to the next node."
            NarrationId.LIST_WATCH_KEEP_FOLLOWING -> "Keep following NEXT."
            NarrationId.LIST_WATCH_NULL_MEANS -> "NULL means there are no more nodes."
            NarrationId.LIST_WATCH_SEARCH_INTRO ->
                "To find ${arg(0)}, start at HEAD and follow the links."
            NarrationId.LIST_WATCH_IS_IT -> "Is ${arg(0)} the target?"
            NarrationId.LIST_WATCH_NOT_IT -> "${arg(0)} is not ${arg(1)}. Keep going."
            NarrationId.LIST_WATCH_FOUND -> "Found it."
            NarrationId.LIST_WATCH_SEARCH_SUMMARY ->
                "A linked list is searched by walking through the nodes."
            NarrationId.LIST_WATCH_INSERT_INTRO ->
                "To insert ${arg(0)}, we have to connect it into the chain."
            NarrationId.LIST_WATCH_NEW_NODE ->
                "Here is ${arg(0)}, made but not connected to anything yet."
            NarrationId.LIST_WATCH_CONNECTED -> "${arg(0)} is now linked in, ahead of ${arg(1)}."
            NarrationId.LIST_WATCH_DELETE_INTRO -> "Now remove ${arg(0)}."
            NarrationId.LIST_WATCH_UNLINK ->
                "To delete a node, reconnect the previous node to the next one."
            NarrationId.LIST_WATCH_GONE -> "${arg(0)} is no longer part of the list."
            NarrationId.LIST_WATCH_INSIGHT -> "The nodes are connected by links, not positions."
            NarrationId.LIST_WATCH_INSIGHT_SUPPORT ->
                "Insert and delete both come down to the same move: change an arrow."
            NarrationId.LIST_WATCH_VS_ARRAY -> "An array jumps. A linked list walks."
            NarrationId.LIST_WATCH_VS_ARRAY_SUPPORT ->
                "In an array you go straight to an index. Here you follow the chain from node to node."
            NarrationId.LIST_WATCH_SUMMARY -> "${arg(0)} nodes, one chain, no positions."
            NarrationId.LIST_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.LIST_PREDICT_PROMPT ->
                "If we delete ${arg(0)}, what should ${arg(1)} point to?"
            NarrationId.LIST_PREDICT_RIGHT -> "Yes — ${arg(0)} links straight to ${arg(1)}."
            NarrationId.LIST_PREDICT_WRONG ->
                "Look at the node after ${arg(0)}. The chain should connect directly to ${arg(1)}."
            NarrationId.LIST_IDEA_1 -> "Traverse by following NEXT."
            NarrationId.LIST_IDEA_2 -> "Insert by reconnecting links."
            NarrationId.LIST_IDEA_3 -> "Delete by reconnecting links."

            // ── Hash Map. Four nouns only — key, value, bucket, hash — and the
            //    copy never uses a fifth. The arithmetic is always written out,
            //    because seeing "12 % 5 = 2" is the moment the structure clicks.
            NarrationId.HASH_ASK_BUCKET_PUT ->
                "Where does key ${arg(0)} go? ${arg(0)} % ${arg(1)} = ?"
            NarrationId.HASH_ASK_BUCKET_GET ->
                "Where should we look for key ${arg(0)}? ${arg(0)} % ${arg(1)} = ?"
            NarrationId.HASH_ASK_BUCKET_REMOVE ->
                "Where does key ${arg(0)} live? ${arg(0)} % ${arg(1)} = ?"
            NarrationId.HASH_ASK_OCCUPIED ->
                "Bucket ${arg(0)} already holds key ${arg(1)}. What happens?"
            NarrationId.HASH_ASK_WHICH_ENTRY -> "Which entry in bucket ${arg(1)} is key ${arg(0)}?"
            NarrationId.HASH_ASK_WHICH_REMOVE -> "Which entry should be removed?"
            NarrationId.HASH_OPTION_BUCKET -> arg(0)
            NarrationId.HASH_OPTION_ENTRY -> "${arg(0)} → ${arg(1)}"
            NarrationId.HASH_OPTION_NOT_HERE -> "NOT HERE"
            NarrationId.HASH_OPTION_CHAIN -> "Keep both"
            NarrationId.HASH_OPTION_REPLACE -> "Replace the value"
            NarrationId.HASH_OPTION_REJECT -> "Refuse it"
            NarrationId.HASH_HASHED -> "${arg(0)} % ${arg(1)} = ${arg(2)}. Bucket ${arg(2)}."
            NarrationId.HASH_COLLISION ->
                "${arg(0)} and ${arg(1)} share bucket ${arg(2)}. That is a collision."
            NarrationId.HASH_UPDATED -> "Key ${arg(0)} is already here, so its value is updated."
            NarrationId.HASH_STORED -> "${arg(0)} → ${arg(1)} is stored in bucket ${arg(2)}."
            NarrationId.HASH_STORED_UPDATE -> "Key ${arg(0)} now holds ${arg(1)}."
            NarrationId.HASH_FOUND -> "Key ${arg(0)} holds ${arg(1)}."
            NarrationId.HASH_NOT_FOUND ->
                "Key ${arg(0)} is not in bucket ${arg(1)}, so it is not in the map at all."
            NarrationId.HASH_REMOVED -> "Key ${arg(0)} is out of bucket ${arg(1)}."
            NarrationId.HASH_NOTHING_TO_REMOVE -> "Key ${arg(0)} was not there, so nothing changed."

            // ── The ladder. Level 1 points, level 2 asks, level 3 does the sum.
            NarrationId.HASH_HINT_REMAINDER ->
                "Divide ${arg(0)} by ${arg(1)} and take the remainder."
            NarrationId.HASH_HINT_KEY_DECIDES ->
                "You never search for the bucket. The key decides it."
            NarrationId.HASH_HINT_SAME_KEY -> "${arg(0)} and ${arg(1)} are the same key."
            NarrationId.HASH_HINT_DIFFERENT_KEYS ->
                "${arg(0)} and ${arg(1)} are different keys that landed together."
            NarrationId.HASH_HINT_CHAIN_RULE ->
                "A bucket is allowed to hold more than one entry."
            NarrationId.HASH_HINT_COMPARE_IN_BUCKET ->
                "Compare ${arg(0)} against each key in this bucket."
            NarrationId.HASH_HINT_BUCKET_NOT_ANSWER ->
                "The right bucket is not the same as the right entry."
            NarrationId.HASH_RETRY_LOOK -> "Work out ${arg(0)} % ${arg(1)} again."
            NarrationId.HASH_RETRY_ASK ->
                "How many whole ${arg(1)}s fit into ${arg(0)}, and what is left over?"
            NarrationId.HASH_RETRY_EXPLAIN ->
                "${arg(0)} % ${arg(1)} = ${arg(2)}, so the key belongs to bucket ${arg(2)}."
            NarrationId.HASH_RETRY_COMPARE_KEYS -> "Compare key ${arg(0)} with key ${arg(1)}."
            NarrationId.HASH_RETRY_ASK_SAME ->
                "They are the same key. Can one key hold two values at once?"
            NarrationId.HASH_RETRY_ASK_DIFFERENT ->
                "They are different keys. Does one have to give up its place?"
            NarrationId.HASH_RETRY_EXPLAIN_UPDATE ->
                "Key ${arg(0)} is already in bucket ${arg(2)}, so the new value replaces the old one."
            NarrationId.HASH_RETRY_EXPLAIN_CHAIN ->
                "${arg(0)} and ${arg(1)} are different keys, so bucket ${arg(2)} keeps both."
            NarrationId.HASH_RETRY_SCAN_LOOK -> "Read the keys in this bucket."
            NarrationId.HASH_RETRY_SCAN_ASK -> "Which of them is exactly ${arg(0)}?"
            NarrationId.HASH_RETRY_SCAN_EXPLAIN -> "Key ${arg(0)} is the one holding ${arg(1)}."
            NarrationId.HASH_RETRY_SCAN_EXPLAIN_MISSING ->
                "No key here is ${arg(0)}, and this is the only bucket it could have been in."
            NarrationId.HASH_WHY_NOT_REJECT ->
                "A hash map does not turn entries away. ${arg(0)} has to go somewhere."
            NarrationId.HASH_WHY_NOT_CHAIN ->
                "Keeping both would leave key ${arg(0)} in the map twice with different values."
            NarrationId.HASH_WHY_NOT_REPLACE ->
                "Replacing would throw away key ${arg(1)}, which nobody asked to remove."

            // ── Hash Map WATCH.
            NarrationId.HASH_WATCH_MEET -> "Meet the Hash Map."
            NarrationId.HASH_WATCH_PURPOSE -> "It stores values, and finds them again using keys."
            NarrationId.HASH_WATCH_KEY_VALUE -> "Every entry is a KEY and a VALUE: ${arg(0)} → ${arg(1)}."
            NarrationId.HASH_WATCH_KEY_FINDS -> "The key is how we find the value later."
            NarrationId.HASH_WATCH_FUNCTION ->
                "The hash function turns a key into a bucket: ${arg(0)} % ${arg(1)} = ${arg(2)}."
            NarrationId.HASH_WATCH_FUNCTION_SUPPORT ->
                "Real hash functions are cleverer. This one is enough to see the idea."
            NarrationId.HASH_WATCH_PUT_HASH -> "PUT ${arg(0)}: ${arg(0)} % ${arg(1)} = ${arg(2)}."
            NarrationId.HASH_WATCH_GET_HASH -> "GET ${arg(0)}: ${arg(0)} % ${arg(1)} = ${arg(2)}."
            NarrationId.HASH_WATCH_REMOVE_HASH ->
                "REMOVE ${arg(0)}: ${arg(0)} % ${arg(1)} = ${arg(2)}."
            NarrationId.HASH_WATCH_JUMP_STRAIGHT ->
                "We jump straight to that bucket. Nothing else is looked at."
            NarrationId.HASH_WATCH_STORED -> "${arg(0)} → ${arg(1)} goes into bucket ${arg(2)}."
            NarrationId.HASH_WATCH_STORED_SUPPORT ->
                "PUT stores the pair in the bucket the key calculated."
            NarrationId.HASH_WATCH_WAIT -> "Wait — bucket ${arg(0)} already holds key ${arg(1)}."
            NarrationId.HASH_WATCH_COLLISION_NAMED ->
                "Two different keys produced the same bucket. That is a COLLISION."
            NarrationId.HASH_WATCH_CHAINING -> "Nothing is lost. The bucket keeps both."
            NarrationId.HASH_WATCH_CHAINING_SUPPORT ->
                "Entries that share a bucket are kept together in a small chain."
            NarrationId.HASH_WATCH_SAME_KEY -> "Key ${arg(0)} is already in this bucket."
            NarrationId.HASH_WATCH_SAME_KEY_SUPPORT ->
                "The same key cannot hold two values, so the value is updated."
            NarrationId.HASH_WATCH_FOUND -> "Found it: key ${arg(0)} holds ${arg(1)}."
            NarrationId.HASH_WATCH_NOT_HERE ->
                "Bucket ${arg(1)} does not hold key ${arg(0)}, so nothing does."
            NarrationId.HASH_WATCH_SCANNED_CHAIN ->
                "We only compared the keys inside bucket ${arg(0)}."
            NarrationId.HASH_WATCH_ONE_BUCKET -> "One bucket checked, and nothing else."
            NarrationId.HASH_WATCH_REMOVED -> "Key ${arg(0)} was right there, and now it is gone."
            NarrationId.HASH_WATCH_REMOVED_SUPPORT ->
                "REMOVE hashes the key, finds the entry, and takes it out."
            NarrationId.HASH_WATCH_WHY_FAST -> "The key tells us where to look."
            NarrationId.HASH_WATCH_WHY_FAST_SUPPORT ->
                "Without hashing you check entries one after another. With it you check one bucket."
            NarrationId.HASH_WATCH_VS_ARRAY -> "An array is found by position. A map is found by key."
            NarrationId.HASH_WATCH_VS_ARRAY_SUPPORT ->
                "You do not need to know where an entry is. You calculate it."
            NarrationId.HASH_WATCH_AVERAGE -> "On average, GET, PUT and REMOVE are close to O(1)."
            NarrationId.HASH_WATCH_AVERAGE_SUPPORT ->
                "That depends on a good hash function and buckets that stay short."
            NarrationId.HASH_WATCH_SUMMARY -> "Key in, bucket out, value found."
            NarrationId.HASH_WATCH_SUMMARY_SUPPORT -> "The idea"
            NarrationId.HASH_PREDICT_PROMPT -> "Which bucket will key ${arg(0)} go to?"
            NarrationId.HASH_PREDICT_RIGHT -> "Yes — ${arg(0)} % ${arg(1)} = ${arg(2)}."
            NarrationId.HASH_PREDICT_WRONG ->
                "Divide ${arg(0)} by ${arg(1)} and look at the remainder."
            NarrationId.HASH_IDEA_1 -> "Hash the key to get a bucket."
            NarrationId.HASH_IDEA_2 -> "Look only inside that bucket."
            NarrationId.HASH_IDEA_3 -> "Two keys may share one bucket. That is fine."

            // ── Fibonacci ─────────────────────────────────────────────────
            // `dp[i]` throughout, because that is the notation the learner will
            // meet everywhere else. The copy never says "the cell before this
            // one": naming the index is what makes the rule portable to a table
            // they have not seen.
            //
            // The feedback always names the two actual numbers on screen. "That
            // is not right" teaches nothing; "3 + 5 = 8, not 6" teaches the rule
            // in the act of correcting it (PRODUCT_SPEC.md §5).
            NarrationId.FIB_OPTION_VALUE -> arg(0)
            NarrationId.FIB_ASK_NEXT -> "What is dp[${arg(0)}]?"
            NarrationId.FIB_BUILT ->
                "dp[${arg(0)}] = ${arg(1)} + ${arg(2)} = ${arg(3)}"

            NarrationId.FIB_HINT_RULE ->
                "dp[${arg(0)}] is the two cells before it added together: " +
                    "dp[${arg(1)}] + dp[${arg(2)}]."

            NarrationId.FIB_RETRY_LOOK ->
                "Look at the two lit cells again — dp[${arg(0)}] and dp[${arg(1)}]."
            NarrationId.FIB_RETRY_ASK ->
                "Fibonacci adds the previous two values. What is ${arg(0)} + ${arg(1)}?"
            NarrationId.FIB_RETRY_EXPLAIN ->
                "dp[${arg(0)}] = ${arg(1)} + ${arg(2)} = ${arg(3)}. Choose ${arg(3)}."

            NarrationId.FIB_WHY_ONLY_PREVIOUS ->
                "That is only the previous value. Fibonacci adds the previous " +
                    "*two*: ${arg(0)} + ${arg(1)} = ${arg(2)}."
            NarrationId.FIB_WHY_DOUBLED_PREVIOUS ->
                "That doubles the previous value. The two cells are " +
                    "${arg(0)} and ${arg(1)}, not ${arg(0)} and ${arg(0)}."
            NarrationId.FIB_WHY_SUBTRACTED ->
                "That subtracts. The rule adds: ${arg(0)} + ${arg(1)} = ${arg(2)}."
            NarrationId.FIB_WHY_REACHED_TOO_FAR ->
                "That is one place further along — it would be dp[${count(3) + 1}], " +
                    "not dp[${arg(3)}]. Add ${arg(0)} and ${arg(1)}."
            NarrationId.FIB_WHY_NOT_THE_TWO ->
                "The two cells to add are ${arg(0)} and ${arg(1)}, and they make ${arg(2)}."

            NarrationId.FIB_CORRECT ->
                "${arg(0)} + ${arg(1)} = ${arg(2)}. dp[${arg(3)}] is worked out once " +
                    "and never again."
            NarrationId.FIB_CORRECT_LAST ->
                "${arg(0)} + ${arg(1)} = ${arg(2)}. The table is full, and " +
                    "dp[${arg(3)}] is the answer."

            NarrationId.FIB_WATCH_SETUP -> "Find F(${arg(0)}) in the Fibonacci sequence."
            NarrationId.FIB_WATCH_SETUP_SUPPORT ->
                "Every number is the sum of the two before it."
            NarrationId.FIB_WATCH_BASE ->
                "F(0) = 0, F(1) = 1, and after that F(n) = F(n−1) + F(n−2)."
            NarrationId.FIB_WATCH_BASE_SUPPORT ->
                "The first two are the definition — nothing produces them, so they " +
                    "are given. Everything else follows from that one rule."

            // The trap, and the reason the rest of the lesson exists. Both
            // numbers come out of the engine, so the argument is checkable.
            NarrationId.FIB_WATCH_NAIVE ->
                "Run that rule as written and F(${arg(0)}) takes ${arg(1)} calls."
            NarrationId.FIB_WATCH_NAIVE_SUPPORT ->
                "It splits in two every time, so the same work repeats: " +
                    "F(${arg(0)}) alone is worked out ${arg(1)} separate times."

            NarrationId.FIB_WATCH_TWO_FIXES ->
                "Dynamic programming: solve each one once, then reuse it."
            NarrationId.FIB_WATCH_TWO_FIXES_SUPPORT ->
                "Memoization keeps the recursion and writes each answer down. " +
                    "Tabulation builds up from the base cases instead — one row, " +
                    "${arg(0)} cells, left to right. That is what happens next."

            NarrationId.FIB_WATCH_BUILD ->
                "dp[${arg(0)}] = ${arg(1)} + ${arg(2)} = ${arg(3)}"
            NarrationId.FIB_WATCH_BUILD_SUPPORT ->
                "dp[${arg(0)}] reads dp[${arg(1)}] and dp[${arg(2)}]. No recursion, " +
                    "no repeats."

            NarrationId.FIB_WATCH_COLLAPSED ->
                "dp[${arg(0)}] to dp[${arg(1)}] follow the same rule: ${arg(2)}."
            NarrationId.FIB_WATCH_COLLAPSED_SUPPORT ->
                "Every one of them is two cells added together, and each takes one step."

            NarrationId.FIB_WATCH_FINAL ->
                "dp[${arg(0)}] = ${arg(1)} + ${arg(2)} = ${arg(3)}"
            NarrationId.FIB_WATCH_FINAL_SUPPORT ->
                "F(${arg(0)}) = ${arg(1)}, in ${arg(2)} steps and no repeated work."

            NarrationId.FIB_WATCH_INSIGHT ->
                "Solve each smaller problem once, and write the answer down."
            NarrationId.FIB_WATCH_INSIGHT_SUPPORT ->
                "${arg(0)} calls became ${arg(1)} — and the saving grows with n. " +
                    "That is the whole of dynamic programming, on the smallest " +
                    "problem that shows it."
            NarrationId.FIB_WATCH_SUMMARY -> "F(${arg(0)}) = ${arg(1)}."
            NarrationId.FIB_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.FIB_IDEA_1 -> "F(0) = 0, F(1) = 1, F(n) = F(n−1) + F(n−2)."
            NarrationId.FIB_IDEA_2 ->
                "Run it as plain recursion and it is about O(2ⁿ) — the same " +
                    "subproblems, over and over."
            NarrationId.FIB_IDEA_3 ->
                "Memoization: keep the recursion, store each answer the first time. " +
                    "O(n) time, O(n) space."
            NarrationId.FIB_IDEA_4 ->
                "Tabulation: start at the base cases and build upward. " +
                    "O(n) time, O(n) space."
            NarrationId.FIB_IDEA_5 ->
                "Either way the rule is the same — never solve the same subproblem twice."

            // ── Caesar Cipher ─────────────────────────────────────────────
            // "Shift" and "wrap" throughout — the two words every description of
            // this cipher uses. A letter's alphabet position is only ever named
            // beside the letter itself, so the learner never has to hold a number
            // they cannot see on screen.
            NarrationId.CC_OPTION_LETTER -> arg(0)
            NarrationId.CC_ASK_LETTER ->
                "${arg(0)} shifted by ${arg(1)} — what does it become?"
            NarrationId.CC_ENCRYPTED -> "${arg(0)} + ${arg(1)} → ${arg(2)}"
            NarrationId.CC_COPIED -> "\"${arg(0)}\" is not a letter, so it stays as it is."

            NarrationId.CC_HINT_COUNT ->
                "Find ${arg(0)} in the alphabet and count ${arg(1)} places forward."

            NarrationId.CC_RETRY_LOOK ->
                "Look at the alphabet again — ${arg(0)}, then ${arg(1)} places forward."
            NarrationId.CC_RETRY_ASK ->
                "${arg(0)} is lit in the mapping row. Which letter is printed under it?"
            NarrationId.CC_RETRY_EXPLAIN ->
                "${arg(0)} is position ${arg(1)}. ${arg(1)} + ${arg(2)} = ${arg(3)}, " +
                    "which is ${arg(4)}."
            NarrationId.CC_RETRY_EXPLAIN_WRAP ->
                "${arg(0)} is position ${arg(1)}. ${arg(1)} + ${arg(2)} = ${arg(3)}, " +
                    "which runs off the end — so subtract 26 and carry on from A. " +
                    "That lands on ${arg(4)}."

            NarrationId.CC_WHY_BACKWARDS ->
                "That is ${arg(0)} shifted ${arg(1)} places *backwards*. Encrypting " +
                    "moves forwards — decrypting is the one that goes back."
            NarrationId.CC_WHY_OFF_BY_ONE ->
                "One place out. Count the letters you move *past*, not the one you " +
                    "start on: ${arg(0)} plus ${arg(1)} is ${arg(2)}."
            NarrationId.CC_WHY_NO_SHIFT ->
                "That is ${arg(0)} unchanged. Every letter moves ${arg(1)} places."
            NarrationId.CC_WHY_WRONG_DISTANCE ->
                "That is the wrong distance from ${arg(0)}. The shift is ${arg(1)}, " +
                    "so ${arg(0)} becomes ${arg(2)}."

            NarrationId.CC_CORRECT ->
                "${arg(0)} plus ${arg(1)} is ${arg(2)}."
            NarrationId.CC_CORRECT_WRAP ->
                "${arg(0)} plus ${arg(1)} runs past Z — ${arg(3)} wraps round to " +
                    "${arg(2)}. The alphabet is a ring."

            NarrationId.CC_WATCH_SETUP -> "Hide \"${arg(0)}\" by shifting every letter."
            NarrationId.CC_WATCH_SETUP_SUPPORT ->
                "Each letter moves a fixed number of places along the alphabet."
            NarrationId.CC_WATCH_RULE -> "The shift is ${arg(0)}."
            NarrationId.CC_WATCH_RULE_SUPPORT ->
                "The mapping row shows every letter and what it becomes — " +
                    "${arg(1)} becomes ${arg(2)}, and so on all the way to Z."

            NarrationId.CC_WATCH_LETTER -> "${arg(0)} + ${arg(1)} → ${arg(2)}"
            NarrationId.CC_WATCH_LETTER_SUPPORT ->
                "Position ${arg(0)}, plus ${arg(1)}. The ciphertext reads ${arg(2)}."
            NarrationId.CC_WATCH_COLLAPSED ->
                "\"${arg(0)}\" follows the same rule, giving ${arg(1)}."
            NarrationId.CC_WATCH_COLLAPSED_SUPPORT ->
                "The same letter always encrypts to the same letter — which is " +
                    "what makes this cipher easy to use, and easy to break."
            NarrationId.CC_WATCH_COPIED -> "\"${arg(0)}\" is not a letter."
            NarrationId.CC_WATCH_COPIED_SUPPORT ->
                "The cipher shifts letters, so everything else passes through untouched."
            NarrationId.CC_WATCH_DONE -> "\"${arg(0)}\" encrypts to \"${arg(1)}\"."

            NarrationId.CC_WATCH_WRAP -> "And past the end: ${arg(0)} + ${arg(1)} → ${arg(2)}."
            NarrationId.CC_WATCH_WRAP_SUPPORT ->
                "${arg(0)} is past Z, so subtract 26 and carry on from A — that is " +
                    "${arg(1)}. Look at the end of the mapping row: X Y Z sit above " +
                    "A B C."

            NarrationId.CC_WATCH_INSIGHT -> "The alphabet is a ring, not a line."
            NarrationId.CC_WATCH_INSIGHT_SUPPORT ->
                "That is all `mod 26` means: add ${arg(0)}, and if you run off the " +
                    "end, come back round to the start."
            NarrationId.CC_WATCH_SUMMARY -> "\"${arg(0)}\" → \"${arg(1)}\"."
            NarrationId.CC_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.CC_IDEA_1 -> "Every letter moves the same number of places."
            NarrationId.CC_IDEA_2 ->
                "encrypted = (position + shift) mod 26 — the mod is the wrap."
            NarrationId.CC_IDEA_3 ->
                "To decrypt, shift back by the same amount: (position − shift + 26) mod 26."
            NarrationId.CC_IDEA_4 ->
                "There are only 25 useful shifts, so anyone can try all of them. " +
                    "This hides a message; it does not secure one."

            // ── XOR Cipher ────────────────────────────────────────────────
            // "The same" and "different" throughout, because that is the rule.
            // The copy never asks the learner to recall a row of a table that is
            // on screen — it asks them to read it.
            NarrationId.XOR_OPTION_BIT -> arg(0)
            NarrationId.XOR_ASK_BIT -> "What is ${arg(0)} ⊕ ${arg(1)}?"
            NarrationId.XOR_SET -> "${arg(0)} ⊕ ${arg(1)} = ${arg(2)}"

            NarrationId.XOR_HINT_RULE ->
                "XOR gives 1 when the two bits are different, and 0 when they are the same."

            NarrationId.XOR_RETRY_LOOK ->
                "Look at the two bits again — ${arg(0)} and ${arg(1)}."
            NarrationId.XOR_RETRY_ASK_SAME -> "Both bits are ${arg(0)}. Are they different?"
            NarrationId.XOR_RETRY_ASK_DIFFERENT ->
                "One bit is ${arg(0)} and the other is ${arg(1)}. Are they different?"
            NarrationId.XOR_RETRY_EXPLAIN_SAME ->
                "${arg(0)} and ${arg(1)} are the same, so XOR gives ${arg(2)}."
            NarrationId.XOR_RETRY_EXPLAIN_DIFFERENT ->
                "${arg(0)} and ${arg(1)} are different, so XOR gives ${arg(2)}."

            // One wrong answer, and which half of the rule it misses depends only
            // on whether the bits match — so the feedback names that half.
            NarrationId.XOR_WHY_SAME_IS_ZERO ->
                "Not quite. XOR gives 0 when both bits are the same, and these are " +
                    "both ${arg(0)}."
            NarrationId.XOR_WHY_DIFFERENT_IS_ONE ->
                "Not quite. XOR gives 1 when the bits are different, and ${arg(0)} " +
                    "and ${arg(1)} are different."

            NarrationId.XOR_CORRECT_SAME ->
                "Both ${arg(0)} — the same, so the result is 0."
            NarrationId.XOR_CORRECT_DIFFERENT ->
                "${arg(0)} and ${arg(1)} are different, so the result is 1."

            NarrationId.XOR_WATCH_SETUP ->
                "XOR compares two bits. The result is 1 when they are different."
            NarrationId.XOR_WATCH_SETUP_SUPPORT ->
                "Hide ${arg(0)} by XORing it with the key ${arg(1)}, one column at a time."
            NarrationId.XOR_WATCH_TABLE -> "That is the whole rule, in four lines."
            NarrationId.XOR_WATCH_TABLE_SUPPORT ->
                "Same bits give 0. Different bits give 1. Nothing else to remember."

            NarrationId.XOR_WATCH_BIT -> "${arg(0)} ⊕ ${arg(1)} = ${arg(2)}"
            NarrationId.XOR_WATCH_BIT_SUPPORT ->
                "The bits are ${arg(0)}. The result so far is ${arg(1)}."
            NarrationId.XOR_WATCH_ENCRYPTED -> "${arg(0)} ⊕ ${arg(1)} = ${arg(2)}"

            NarrationId.XOR_WATCH_REVERSE ->
                "Now XOR the ciphertext ${arg(0)} with the same key ${arg(1)}."
            NarrationId.XOR_WATCH_REVERSE_SUPPORT ->
                "${arg(0)} ⊕ ${arg(1)} = ${arg(2)} — and that is the first bit of the " +
                    "original back."
            NarrationId.XOR_WATCH_RECOVERED -> "The rest come back the same way: ${arg(0)}."
            NarrationId.XOR_WATCH_RECOVERED_SUPPORT ->
                "Which is exactly the ${arg(0)} we started with."

            NarrationId.XOR_WATCH_INSIGHT -> "The same key, applied twice, gives the original back."
            NarrationId.XOR_WATCH_INSIGHT_SUPPORT ->
                "XOR undoes itself, so encrypting and decrypting are not two " +
                    "procedures — ${arg(0)} became ${arg(1)}, and the same pass " +
                    "turned it back."
            NarrationId.XOR_WATCH_SUMMARY -> "${arg(0)} ⊕ key = ${arg(1)}, and back again."
            NarrationId.XOR_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.XOR_IDEA_1 -> "XOR gives 1 when two bits differ, 0 when they match."
            NarrationId.XOR_IDEA_2 ->
                "plaintext ⊕ key = ciphertext, and ciphertext ⊕ key = plaintext."
            NarrationId.XOR_IDEA_3 ->
                "One pass over n bits, so O(n) time and O(n) space."
            // The caveat, last, where a recap bullet is read rather than skipped.
            NarrationId.XOR_IDEA_4 ->
                "XOR is a building block of real cryptography, but a XOR cipher with " +
                    "a short or reused key is not secure on its own. This is a " +
                    "demonstration of the operation, not a way to protect anything."

            // ── SHA-256 Hashing ───────────────────────────────────────────
            // **This lesson is about a hash function, and the copy never calls it
            // encryption.** The words "encrypt", "decrypt", "cipher" and "key"
            // appear here only where the point is that none of them applies — a
            // learner who leaves thinking SHA-256 encrypts has been taught
            // something false about a real algorithm, which is worse than being
            // taught nothing.
            //
            // Two other wordings are deliberate and should not be "tidied":
            //  - never "impossible to reverse". The true claim is *computationally
            //    infeasible from the hash alone*, and the difference is the whole
            //    of why rainbow tables and salting exist;
            //  - never "SHA-256 is how passwords are stored". It is fast by
            //    design, which is exactly wrong for a password, and the recap
            //    names the algorithms that are built for the job.
            NarrationId.SHA_HASHED -> "“${arg(0)}” → ${arg(1)}…"

            // -- Fixed length --------------------------------------------------
            NarrationId.SHA_ASK_FIXED_LENGTH -> "Which statement is correct?"
            NarrationId.SHA_OPTION_FIXED -> "FIXED"
            NarrationId.SHA_OPTION_VARIES -> "VARIES"
            NarrationId.SHA_HINT_FIXED_LENGTH ->
                "Count the characters in each hash above. Then count them again on " +
                    "the next row."
            NarrationId.SHA_RETRY_LOOK_FIXED_LENGTH ->
                "Look at the rows again — the messages are different lengths."
            NarrationId.SHA_RETRY_ASK_FIXED_LENGTH ->
                "One message is much longer than the other. Are their hashes " +
                    "different lengths too?"
            NarrationId.SHA_RETRY_EXPLAIN_FIXED_LENGTH ->
                "Every hash on screen is 64 characters, however long its message " +
                    "was. SHA-256 always produces a fixed-length output."
            NarrationId.SHA_WHY_NOT_VARIES ->
                "Not quite. SHA-256 always produces a 256-bit output — the rows " +
                    "above have very different messages and hashes of exactly the " +
                    "same length."
            NarrationId.SHA_CORRECT_FIXED_LENGTH ->
                "Correct. SHA-256 always produces a 256-bit hash, whatever goes in."

            // -- Determinism ---------------------------------------------------
            NarrationId.SHA_ASK_DETERMINISTIC -> "Which statement is correct?"
            NarrationId.SHA_OPTION_SAME -> "SAME"
            NarrationId.SHA_OPTION_RANDOM -> "RANDOM"
            NarrationId.SHA_HINT_DETERMINISTIC ->
                "The same message was hashed twice, as two separate runs. Compare " +
                    "the two hashes."
            NarrationId.SHA_RETRY_LOOK_DETERMINISTIC ->
                "Look at the two rows again — they are the same message."
            NarrationId.SHA_RETRY_ASK_DETERMINISTIC ->
                "The same message went through twice. Did anything about the hash " +
                    "change?"
            NarrationId.SHA_RETRY_EXPLAIN_DETERMINISTIC ->
                "The two hashes are identical, character for character. The same " +
                    "input always produces the same SHA-256 hash."
            NarrationId.SHA_WHY_NOT_RANDOM ->
                "Not quite. Nothing about SHA-256 is random — the two rows above " +
                    "are the same message hashed twice, and the hashes match " +
                    "exactly. A hash that changed each time could never verify " +
                    "anything."
            NarrationId.SHA_CORRECT_DETERMINISTIC ->
                "Correct. The same input always produces the same SHA-256 hash."

            // -- The avalanche effect ------------------------------------------
            NarrationId.SHA_ASK_AVALANCHE ->
                "One character changed. What happens to the hash?"
            NarrationId.SHA_OPTION_UNCHANGED -> "SAME"
            NarrationId.SHA_OPTION_DIFFERENT -> "DIFFERENT"
            NarrationId.SHA_HINT_AVALANCHE ->
                "The two messages differ by one character. The changed parts of the " +
                    "hash are marked."
            NarrationId.SHA_RETRY_LOOK_AVALANCHE ->
                "Look at the two hashes again — the marked characters are the ones " +
                    "that changed."
            NarrationId.SHA_RETRY_ASK_AVALANCHE ->
                "Only one character of the message changed. Does the second hash " +
                    "still look anything like the first?"
            NarrationId.SHA_RETRY_EXPLAIN_AVALANCHE ->
                "Almost every character of the hash is different, from a single " +
                    "changed letter. That is the avalanche effect."
            NarrationId.SHA_WHY_NOT_UNCHANGED ->
                "Not quite. A one-character change rewrites almost the whole hash — " +
                    "that is what makes a hash useful for spotting that a file was " +
                    "altered."
            NarrationId.SHA_CORRECT_AVALANCHE ->
                "Correct. A tiny change to the input produces a completely " +
                    "different hash."

            // -- One-way -------------------------------------------------------
            NarrationId.SHA_ASK_ONE_WAY ->
                "Can a SHA-256 hash normally be turned back into its input?"
            NarrationId.SHA_OPTION_YES_KEY -> "YES"
            NarrationId.SHA_OPTION_NO_ONE_WAY -> "NO"
            NarrationId.SHA_HINT_ONE_WAY ->
                "Look at the pipeline. There is no key going in, and no arrow " +
                    "coming back."
            NarrationId.SHA_RETRY_LOOK_ONE_WAY ->
                "Look at the pipeline again — every arrow points one way."
            NarrationId.SHA_RETRY_ASK_ONE_WAY ->
                "A cipher needs a key to undo it. Where would SHA-256's key go?"
            NarrationId.SHA_RETRY_EXPLAIN_ONE_WAY ->
                "Hashing is not encryption. There is no key and no decrypt step, " +
                    "and 64 characters could not hold a message of any length " +
                    "anyway — so the input cannot normally be recovered."
            NarrationId.SHA_WHY_NOT_DECRYPTABLE ->
                "Not quite — that is encryption, not hashing. SHA-256 has no key " +
                    "and no reverse operation: it is designed to be computationally " +
                    "infeasible to reverse from the hash alone."
            NarrationId.SHA_CORRECT_ONE_WAY ->
                "Correct. Hashing is designed as a one-way operation — there is no " +
                    "key and nothing to decrypt."

            // -- Output size ---------------------------------------------------
            NarrationId.SHA_ASK_OUTPUT_SIZE -> "What does SHA-256 produce?"
            NarrationId.SHA_OPTION_ENCRYPTED_MESSAGE -> "A MESSAGE"
            NarrationId.SHA_OPTION_256_BIT_HASH -> "256 BITS"
            NarrationId.SHA_HINT_OUTPUT_SIZE ->
                "The name says the size. Read the last two stages of the pipeline."
            NarrationId.SHA_RETRY_LOOK_OUTPUT_SIZE ->
                "Look at the end of the pipeline again."
            NarrationId.SHA_RETRY_ASK_OUTPUT_SIZE ->
                "Is the output a message you could read back, or a fixed-size " +
                    "fingerprint?"
            NarrationId.SHA_RETRY_EXPLAIN_OUTPUT_SIZE ->
                "SHA-256 produces a 256-bit hash — 32 bytes, written as 64 " +
                    "hexadecimal characters. It is a fingerprint, not a message."
            NarrationId.SHA_WHY_NOT_ENCRYPTED_MESSAGE ->
                "Not quite. SHA-256 does not encrypt anything, and its output is " +
                    "never variable-length: it is always a 256-bit hash."
            NarrationId.SHA_CORRECT_OUTPUT_SIZE ->
                "Correct. SHA-256 produces a 256-bit hash — always 64 hexadecimal " +
                    "characters."

            // -- What WATCH says as each property lands ------------------------
            NarrationId.SHA_SETTLED_FIXED_LENGTH ->
                "Different messages. The same output length."
            NarrationId.SHA_SETTLED_DETERMINISTIC ->
                "The same input always produces the same hash."
            NarrationId.SHA_SETTLED_AVALANCHE ->
                "Change one character, and almost all of the hash changes."
            NarrationId.SHA_SETTLED_ONE_WAY ->
                "A hash does not go back."
            NarrationId.SHA_SETTLED_OUTPUT_SIZE ->
                "Where hashes are actually used."

            // -- WATCH ---------------------------------------------------------
            NarrationId.SHA_WATCH_SETUP ->
                "A hash function turns data into a fixed-size value."
            NarrationId.SHA_WATCH_SETUP_SUPPORT ->
                "Anything can go in — a word, a sentence, a whole file. What comes " +
                    "out is always the same size."
            NarrationId.SHA_WATCH_SIZES -> "SHA-256 is one such function."
            NarrationId.SHA_WATCH_SIZES_SUPPORT ->
                "Its output is always ${arg(0)} bits — ${arg(1)} bytes, written as " +
                    "${arg(2)} hexadecimal characters. Those are three ways of " +
                    "saying one size."
            NarrationId.SHA_WATCH_FIRST -> "Hashing “${arg(0)}”."
            NarrationId.SHA_WATCH_FIRST_SUPPORT ->
                "${arg(0)} — ${arg(1)} characters, and the same every time you hash " +
                    "that word."
            // Also the hand-over from hashing to comparing, so it says every
            // message went through the same pipeline before reading the lengths.
            NarrationId.SHA_WATCH_FIXED_LENGTH_SUPPORT ->
                "All ${arg(0)} messages went through the same pipeline. ${arg(1)} " +
                    "characters in, ${arg(2)} characters in — and ${arg(3)} " +
                    "hexadecimal characters out, every time."
            NarrationId.SHA_WATCH_DETERMINISTIC_SUPPORT ->
                "“${arg(0)}” went through twice, as two separate runs, and " +
                    "came out identical. That is what makes a hash worth comparing " +
                    "against."
            NarrationId.SHA_WATCH_AVALANCHE_SUPPORT ->
                "“${arg(0)}” and “${arg(1)}” differ by one " +
                    "character, and ${arg(2)} of the ${arg(3)} hash characters " +
                    "changed with it."
            NarrationId.SHA_WATCH_ONE_WAY_SUPPORT ->
                "There is no key and no decrypt step. SHA-256 is designed to be " +
                    "computationally infeasible to reverse from the hash alone."
            NarrationId.SHA_WATCH_USES_SUPPORT ->
                "Checking a download arrived intact, spotting that a file changed, " +
                    "and as one part of digital signatures."

            NarrationId.SHA_WATCH_INSIGHT ->
                "Any input, any length — always ${arg(0)} bits, and never back again."
            NarrationId.SHA_WATCH_INSIGHT_SUPPORT ->
                "That one-way arrow is the difference between hashing and " +
                    "encryption. A cipher is meant to be undone; a hash is not."
            NarrationId.SHA_WATCH_SUMMARY ->
                "Input → fixed-size fingerprint."
            NarrationId.SHA_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.SHA_IDEA_1 ->
                "Hashing turns any input into a fixed-size value — SHA-256 always " +
                    "gives ${arg(0)} bits, written as ${arg(1)} hexadecimal " +
                    "characters."
            NarrationId.SHA_IDEA_2 ->
                "The same input always gives the same hash, and different inputs " +
                    "normally give different ones."
            NarrationId.SHA_IDEA_3 ->
                "Change one character and almost the whole hash changes."
            NarrationId.SHA_IDEA_4 ->
                "One pass over the message, so O(n) in its length at a high level. " +
                    "Inside, it is padded into 512-bit blocks and each block runs 64 " +
                    "compression rounds — real work this lesson deliberately does " +
                    "not draw."
            // The two a learner must not leave without, last, where a recap bullet
            // is read rather than skipped.
            NarrationId.SHA_IDEA_5 ->
                "Hashing is not encryption. There is no key and no decrypt step, and " +
                    "SHA-256 is designed to be computationally infeasible to reverse " +
                    "from the hash alone."
            NarrationId.SHA_IDEA_6 ->
                "SHA-256 on its own is not how passwords should be stored — it is " +
                    "built to be fast, which is the wrong property for a password. " +
                    "Password systems use algorithms made for the job, such as " +
                    "Argon2, bcrypt or scrypt."

            // ── AES ───────────────────────────────────────────────────────────
            // **This lesson is about a block cipher, and the copy is careful in
            // three places that should not be "tidied":
            //
            //  - never "unbreakable", and never "impossible to break". What is
            //    true is that no practical attack is known that beats trying every
            //    key, which is a claim a learner can go and check. The stronger
            //    sentence is one they would later have to unlearn;
            //  - never a suggestion that encrypting a message means encrypting its
            //    blocks. A block cipher is a primitive; turning it into a way to
            //    send a message takes a mode of operation, and the recap names
            //    AES-GCM. ECB appears once, as the thing not to reach for;
            //  - never "AES keeps your data safe" on its own. Key management, an
            //    authenticated mode and a protocol around it are what do that, and
            //    the last recap bullet says so.
            //
            // The learner is also never asked to perform the arithmetic. SubBytes
            // is a table lookup, MixColumns is multiplication in GF(2^8), and
            // PRODUCT_SPEC.md §3 gives the app the arithmetic — so the copy
            // explains what each step *achieves* and never asks for a byte.

            // -- Labels that carry their own text ------------------------------
            NarrationId.AES_OPTION_NUMBER -> arg(0)
            NarrationId.AES_OPTION_TRANSFORMATION -> arg(0)
            NarrationId.AES_OPTION_KEY_EXPANSION -> "EXPANSION"
            NarrationId.AES_OPTION_SBOX -> "S-BOX"

            // -- What the app says as each step of the real run lands -----------
            NarrationId.AES_STEP_PLAINTEXT -> "The message, before anything happens."
            NarrationId.AES_STEP_BLOCK -> "${arg(0)} bits — ${arg(1)} bytes."
            NarrationId.AES_STEP_STATE -> "Arranged as a ${arg(0)} × ${arg(1)} State."
            NarrationId.AES_STEP_KEY_EXPANSION ->
                "The ${arg(0)}-bit key becomes ${arg(1)} round keys."
            NarrationId.AES_STEP_INITIAL_ADD_ROUND_KEY ->
                "AddRoundKey, before the rounds begin."
            NarrationId.AES_STEP_TRANSFORM -> "Round ${arg(0)} / ${arg(1)} · ${arg(2)}"
            NarrationId.AES_STEP_CIPHERTEXT -> "${arg(0)} rounds done."
            NarrationId.AES_STEP_VARIANTS -> "Three key sizes, one block size."
            NarrationId.AES_STEP_DECRYPTION -> "And the same steps, backwards."

            // -- Exercise 1 · the block size -----------------------------------
            NarrationId.AES_ASK_BLOCK_SIZE -> "How large is an AES block, in bits?"
            NarrationId.AES_HINT_BLOCK_SIZE ->
                "Count the bytes in the State — there are ${arg(0)} of them, and a " +
                    "byte is 8 bits."
            NarrationId.AES_RETRY_LOOK_BLOCK_SIZE ->
                "Look at the State again, and count what is in it."
            NarrationId.AES_RETRY_ASK_BLOCK_SIZE ->
                "${arg(0)} bytes are on screen. How many bits is that?"
            NarrationId.AES_RETRY_EXPLAIN_BLOCK_SIZE ->
                "${arg(0)} bytes × 8 bits = ${arg(1)}. An AES block is ${arg(1)} bits, " +
                    "in every variant."
            NarrationId.AES_WHY_BLOCK_TOO_SMALL ->
                "That is DES's block size, and it is half of AES's. One of the " +
                    "reasons AES replaced it."
            NarrationId.AES_WHY_BLOCK_IS_KEY_SIZE ->
                "${arg(0)} is an AES **key** size, not a block size — that is the " +
                    "number in the name AES-${arg(0)}. The block does not change " +
                    "between variants."
            NarrationId.AES_CORRECT_BLOCK_SIZE ->
                "Correct. AES always works on a ${arg(0)}-bit block — ${arg(1)} bytes, " +
                    "whichever key size it uses."

            // -- Exercise 2 · the State ----------------------------------------
            NarrationId.AES_ASK_STATE_SIZE -> "How many bytes does the State hold?"
            NarrationId.AES_HINT_STATE_SIZE ->
                "It is ${arg(0)} rows by ${arg(1)} columns, and every cell is one byte."
            NarrationId.AES_RETRY_LOOK_STATE_SIZE -> "Count the cells on screen."
            NarrationId.AES_RETRY_ASK_STATE_SIZE ->
                "${arg(0)} rows, ${arg(1)} columns. How many cells is that?"
            NarrationId.AES_RETRY_EXPLAIN_STATE_SIZE ->
                "${arg(0)} × ${arg(1)} = ${arg(2)} cells, one byte each — which is " +
                    "the ${arg(3)}-bit block, rearranged."
            NarrationId.AES_WHY_STATE_ONE_ROW ->
                "That is one row, or one column. The State has ${arg(0)} of each."
            NarrationId.AES_WHY_STATE_HALF_BLOCK ->
                "8 bytes is 64 bits — half a block. The State holds a whole one."
            NarrationId.AES_WHY_STATE_IS_KEY_SIZE ->
                "32 bytes is 256 bits, which is AES-256's **key**. The State holds " +
                    "the block, and that is 16 bytes in every variant."
            NarrationId.AES_CORRECT_STATE_SIZE ->
                "Correct. ${arg(0)} × ${arg(1)} = ${arg(2)} bytes — the ${arg(3)}-bit " +
                    "block, laid out as a square."

            // -- Exercise 3 · the order of a normal round ----------------------
            NarrationId.AES_ASK_NEXT_TRANSFORMATION ->
                "A normal round, step ${arg(0)} of ${arg(1)}. Tap what comes next."
            NarrationId.AES_HINT_NEXT_TRANSFORMATION ->
                "Substitute, then move, then mix, then add the key."
            NarrationId.AES_RETRY_LOOK_NEXT_TRANSFORMATION ->
                "Look at the round again — the steps you have placed are filled in."
            NarrationId.AES_RETRY_ASK_NEXT_TRANSFORMATION ->
                "Step ${arg(0)}. What has the round done so far, and what is left?"
            NarrationId.AES_RETRY_EXPLAIN_NEXT_TRANSFORMATION ->
                "Step ${arg(0)} of a normal round is ${arg(1)}. Tap it."
            NarrationId.AES_WHY_TRANSFORMATION_ALREADY_DONE ->
                "${arg(0)} has already run in this round. Each step happens once."
            NarrationId.AES_WHY_TRANSFORMATION_LATER ->
                "${arg(0)} is in the round, but not yet — something comes before it."
            NarrationId.AES_CORRECT_SUB_BYTES ->
                "Correct. SubBytes first: every byte is swapped through the S-box, " +
                    "and nothing moves."
            NarrationId.AES_CORRECT_SHIFT_ROWS ->
                "Correct. ShiftRows next: row r rotates left by r, so row 0 stays " +
                    "put and the other three slide. Values do not change — only " +
                    "where they sit."
            NarrationId.AES_CORRECT_MIX_COLUMNS ->
                "Correct. MixColumns: each column is mixed into itself, so every " +
                    "byte now depends on all four above it. That is what spreads a " +
                    "change sideways."
            NarrationId.AES_CORRECT_ADD_ROUND_KEY ->
                "Correct. AddRoundKey last: the State is XORed with this round's " +
                    "key. It is the only step the key touches — without it the " +
                    "round would be a fixed scramble anyone could undo."

            // -- Exercise 4 · what the final round leaves out -------------------
            NarrationId.AES_ASK_SKIPPED ->
                "Round ${arg(0)} is the last one. Tap the step it leaves out."
            NarrationId.AES_HINT_SKIPPED ->
                "Three of these four ran in the final round you just watched."
            NarrationId.AES_RETRY_LOOK_SKIPPED ->
                "Look back at round ${arg(0)} — one of these four was struck through."
            NarrationId.AES_RETRY_ASK_SKIPPED ->
                "Which step mixes the columns together? That is the one the last " +
                    "round does without."
            NarrationId.AES_RETRY_EXPLAIN_SKIPPED ->
                "The final round is SubBytes → ShiftRows → AddRoundKey. MixColumns " +
                    "is the one that is left out."
            NarrationId.AES_WHY_NOT_SKIPPED ->
                "${arg(0)} runs in every round, including the last one."
            NarrationId.AES_CORRECT_SKIPPED ->
                "Correct. The final round is SubBytes → ShiftRows → AddRoundKey. " +
                    "Mixing the columns at the very end would add nothing that " +
                    "decryption could not immediately undo, so the standard does " +
                    "not spend it."

            // -- Exercise 5 · rounds per variant -------------------------------
            NarrationId.AES_ASK_ROUND_COUNT -> "How many rounds does ${arg(0)} run?"
            NarrationId.AES_HINT_ROUND_COUNT ->
                "A longer key buys more rounds, two at a time."
            NarrationId.AES_RETRY_LOOK_ROUND_COUNT ->
                "${arg(0)} has a ${arg(1)}-bit key. Look at the table again."
            NarrationId.AES_RETRY_ASK_ROUND_COUNT ->
                "The three counts go up in twos. Which one belongs to this key size?"
            NarrationId.AES_RETRY_EXPLAIN_ROUND_COUNT ->
                "${arg(0)} has a ${arg(1)}-bit key and runs ${arg(2)} rounds."
            NarrationId.AES_WHY_ROUND_COUNT ->
                "${arg(0)} rounds is ${arg(1)}'s, not ${arg(2)}'s."
            NarrationId.AES_CORRECT_ROUND_COUNT ->
                "Correct. ${arg(0)} runs ${arg(1)} rounds."

            // -- Exercise 6 · where the round keys come from --------------------
            NarrationId.AES_ASK_KEY_EXPANSION -> "What produces the round keys?"
            NarrationId.AES_HINT_KEY_EXPANSION ->
                "One key went in at the start. More than one came out."
            NarrationId.AES_RETRY_LOOK_KEY_EXPANSION ->
                "Look at the key panel — one key, and a list under it."
            NarrationId.AES_RETRY_ASK_KEY_EXPANSION ->
                "There is one key and ${arg(0)} round keys. What turned one into " +
                    "the other?"
            NarrationId.AES_RETRY_EXPLAIN_KEY_EXPANSION ->
                "Key Expansion takes the ${arg(0)}-bit key and derives ${arg(1)} " +
                    "round keys from it."
            NarrationId.AES_WHY_NOT_SBOX ->
                "The S-box substitutes bytes inside SubBytes. Key Expansion uses it " +
                    "along the way, but the S-box is a lookup table — it is not " +
                    "what produces the round keys."
            NarrationId.AES_CORRECT_KEY_EXPANSION ->
                "Correct. Key Expansion derives ${arg(0)} round keys from the one " +
                    "key — one for each of the ${arg(1)} rounds, plus the one spent " +
                    "before they start."

            // -- WATCH ----------------------------------------------------------
            NarrationId.AES_WATCH_SETUP -> "AES is a symmetric block cipher."
            NarrationId.AES_WATCH_SETUP_SUPPORT ->
                "Symmetric means one key both ways — the same key encrypts and " +
                    "decrypts. Block means it works on a fixed number of bytes at a " +
                    "time."
            NarrationId.AES_WATCH_PLAINTEXT -> "Start with the message."
            NarrationId.AES_WATCH_PLAINTEXT_SUPPORT ->
                "Sixteen characters, which is exactly what AES takes at once."
            NarrationId.AES_WATCH_BLOCK -> "One block is ${arg(0)} bits — ${arg(1)} bytes."
            NarrationId.AES_WATCH_BLOCK_SUPPORT ->
                "That never changes. AES-128, AES-192 and AES-256 all take the same " +
                    "block; the number in the name is the key."
            NarrationId.AES_WATCH_STATE -> "The bytes become a ${arg(0)} × ${arg(1)} State."
            NarrationId.AES_WATCH_STATE_SUPPORT ->
                "Filled column by column — byte 0 top-left, byte 1 beneath it, byte " +
                    "4 at the top of the next column. All ${arg(0)} of them, and " +
                    "every transformation from here works on this square."
            NarrationId.AES_WATCH_KEY_EXPANSION -> "The key is expanded."
            NarrationId.AES_WATCH_KEY_EXPANSION_SUPPORT ->
                "Key Expansion turns the ${arg(0)}-bit key into ${arg(1)} round " +
                    "keys — one for each of the ${arg(2)} rounds, plus one spent " +
                    "before they begin."
            NarrationId.AES_WATCH_INITIAL_ADD_ROUND_KEY ->
                "AddRoundKey, before round 1."
            NarrationId.AES_WATCH_INITIAL_ADD_ROUND_KEY_SUPPORT ->
                "The State is XORed with round key 0. Without this the first " +
                    "SubBytes would be a fixed substitution with no key in it at all."
            NarrationId.AES_WATCH_TRANSFORM -> "Round ${arg(0)} of ${arg(1)}: ${arg(2)}."
            NarrationId.AES_WATCH_FINAL_TRANSFORM ->
                "The final round, ${arg(0)} of ${arg(1)}: ${arg(2)}."
            NarrationId.AES_WATCH_SUB_BYTES_SUPPORT ->
                "Every one of the ${arg(1)} bytes is swapped for its S-box entry. " +
                    "All ${arg(0)} changed, and not one of them moved."
            NarrationId.AES_WATCH_SHIFT_ROWS_SUPPORT ->
                "Row 1 rotates one place left, row 2 two, row 3 three — and row 0 " +
                    "stays where it is. ${arg(0)} of ${arg(1)} bytes moved, and no " +
                    "value changed."
            NarrationId.AES_WATCH_MIX_COLUMNS_SUPPORT ->
                "Each column is mixed into itself, so every byte now depends on all " +
                    "four that were above it. This is what carries a change " +
                    "sideways; ShiftRows is what carries it between columns."
            NarrationId.AES_WATCH_ADD_ROUND_KEY_SUPPORT ->
                "The State is XORed with this round's key. It is the only step the " +
                    "key touches, and it is why the round cannot be undone by " +
                    "someone who does not have it."
            NarrationId.AES_WATCH_FINAL_ROUND_OMITS ->
                "And that is the round finished — with no ${arg(0)}. The last round " +
                    "leaves it out: mixing at the very end would add nothing that " +
                    "decryption could not immediately undo."
            NarrationId.AES_WATCH_MIDDLE_ROUNDS ->
                "Rounds ${arg(0)} to ${arg(1)} are the same four steps again."
            NarrationId.AES_WATCH_MIDDLE_ROUNDS_SUPPORT ->
                "${arg(0)} more rounds of SubBytes → ShiftRows → MixColumns → " +
                    "AddRoundKey. Round 2 is on screen; the rest run exactly the " +
                    "same way, each with its own round key."
            NarrationId.AES_WATCH_CIPHERTEXT -> "${arg(0)} rounds later: the ciphertext."
            NarrationId.AES_WATCH_CIPHERTEXT_SUPPORT ->
                "${arg(0)} — the same ${arg(1)} bytes that went in, and nothing " +
                    "about them left to read."
            NarrationId.AES_WATCH_VARIANTS -> "Three variants, and one block size."
            NarrationId.AES_WATCH_VARIANTS_SUPPORT ->
                "The number in the name is the key size. The block is ${arg(0)} bits " +
                    "in all three — a longer key buys more rounds, not a bigger block."
            NarrationId.AES_WATCH_DECRYPTION -> "And it goes back."
            NarrationId.AES_WATCH_DECRYPTION_SUPPORT ->
                "Decryption runs the inverse transformations in reverse order, with " +
                    "the same round keys. That is what symmetric means, and it is " +
                    "the whole difference from a hash."

            NarrationId.AES_WATCH_INSIGHT ->
                "The same four steps, ${arg(0)} times over — and the last round " +
                    "leaves one out."
            NarrationId.AES_WATCH_INSIGHT_SUPPORT ->
                "Substitute, shift, mix, add the key. Everything else about AES is " +
                    "how many times and with which key."
            NarrationId.AES_WATCH_SUMMARY -> "One block in, one block out."
            NarrationId.AES_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.AES_IDEA_1 ->
                "AES is a symmetric block cipher: one key both ways, and always a " +
                    "${arg(0)}-bit block — ${arg(1)} bytes."
            NarrationId.AES_IDEA_2 ->
                "Those bytes are held as a ${arg(0)} × ${arg(1)} State, filled " +
                    "column by column, and every transformation works on it."
            NarrationId.AES_IDEA_3 ->
                "Key Expansion derives one round key per round from the original " +
                    "key, plus one for the AddRoundKey before the rounds start."
            NarrationId.AES_IDEA_4 ->
                "A round is SubBytes → ShiftRows → MixColumns → AddRoundKey. The " +
                    "final round leaves MixColumns out."
            NarrationId.AES_IDEA_5 ->
                "AES-128 runs 10 rounds, AES-192 twelve and AES-256 fourteen. " +
                    "Decryption is the inverse steps in reverse, with the same key."
            // The two a learner must not leave without, last, where a recap bullet
            // is read rather than skipped.
            NarrationId.AES_IDEA_6 ->
                "A block cipher on its own is not a way to encrypt a message. It " +
                    "needs a mode of operation, and a real system wants an " +
                    "authenticated one such as AES-GCM — not ECB, which encrypts " +
                    "identical blocks identically and leaks the shape of the data."
            NarrationId.AES_IDEA_7 ->
                "AES is not \"unbreakable\", and it is not a whole security system. " +
                    "What is true is that no practical attack is known that beats " +
                    "trying every key — and that safety in practice comes just as " +
                    "much from key management, a sound mode and the protocol around " +
                    "it."

            // ── RSA ───────────────────────────────────────────────────────────
            // **The first asymmetric lesson, and the one with the most ways to
            // mislead.** Three wordings are load-bearing and should not be
            // "tidied":
            //
            //  - never a claim that these numbers are secure. n = 55 factors by
            //    inspection, so anyone holding the public key has the private one
            //    too. The caveat is on the picture for the whole lesson, gets a
            //    beat of its own, and the recap names what real use takes: large
            //    parameters and OAEP padding;
            //  - never "anyone can encrypt and only you can ever decrypt" as an
            //    unqualified promise. What is true is narrower, and is what the
            //    copy says: the private key is what undoes the public key's work,
            //    and it is the half that is kept;
            //  - never a suggestion that a learner implement this. Textbook RSA is
            //    deterministic and unpadded, which is exactly why it is a teaching
            //    device and not a recipe.
            //
            // The learner is also never asked to grind. 9^27 is a twenty-six digit
            // number, and PRODUCT_SPEC.md §3 gives the app the arithmetic — so
            // every question prints its formula with the operands filled in and
            // asks which value it produces.

            // -- Labels that carry their own text -------------------------------
            NarrationId.RSA_OPTION_NUMBER -> arg(0)
            NarrationId.RSA_OPTION_PAIR -> "(${arg(0)}, ${arg(1)})"
            NarrationId.RSA_OPTION_TEXT -> arg(0)

            // -- What the app says as each beat lands ---------------------------
            NarrationId.RSA_STEP_MESSAGE -> "The message is “${arg(0)}”."
            NarrationId.RSA_STEP_ASYMMETRIC -> "Asymmetric cryptography."
            NarrationId.RSA_STEP_KEY_REVEAL -> "A public key and a private key."
            NarrationId.RSA_STEP_SHAREABLE_KEY -> "The public key is the one you share."
            NarrationId.RSA_STEP_ENCRYPT_OPERATION -> "RSA encrypts it."
            NarrationId.RSA_STEP_ENCRYPT_KEY -> "The public key encrypts."
            NarrationId.RSA_STEP_DECRYPT_KEY -> "The private key decrypts."
            NarrationId.RSA_STEP_CIPHERTEXT -> "The ciphertext is ${arg(0)}."
            NarrationId.RSA_STEP_RECOVERED -> "“${arg(0)}” is back."
            NarrationId.RSA_STEP_DECRYPT_OPERATION -> "The private key decrypts."
            NarrationId.RSA_STEP_TEXT_AS_NUMBERS -> "Text is stored as numbers."
            NarrationId.RSA_STEP_TOY_EXAMPLE -> "A toy example: m = ${arg(0)}."
            NarrationId.RSA_STEP_TOY_CIPHERTEXT -> "${arg(0)} has become ${arg(1)}."
            NarrationId.RSA_STEP_CLOSING_FLOW -> "“${arg(0)}”, out and back."
            NarrationId.RSA_STEP_KEY_ORIGIN -> "Now — where did those keys come from?"
            NarrationId.RSA_STEP_KEY_PAIR_PURPOSE ->
                "Encrypt with one, decrypt with the other."
            NarrationId.RSA_STEP_PRIMES -> "p = ${arg(0)}, q = ${arg(1)}."
            NarrationId.RSA_STEP_MODULUS -> "n = ${arg(0)} × ${arg(1)} = ${arg(2)}."
            NarrationId.RSA_STEP_TOTIENT ->
                "φ(n) = ${arg(0)} × ${arg(1)} = ${arg(2)}."
            NarrationId.RSA_STEP_PUBLIC_EXPONENT ->
                "e = ${arg(0)}, and gcd(${arg(0)}, ${arg(1)}) = 1."
            NarrationId.RSA_STEP_PRIVATE_EXPONENT ->
                "d = ${arg(0)}, because ${arg(1)} × ${arg(0)} = ${arg(2)}, which is " +
                    "1 more than a multiple of ${arg(3)}."
            NarrationId.RSA_STEP_PUBLIC_KEY -> "Public key ${arg(0)}."
            NarrationId.RSA_STEP_PRIVATE_KEY -> "Private key ${arg(0)}."
            NarrationId.RSA_STEP_ENCRYPT ->
                "c = ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)}."
            NarrationId.RSA_STEP_DECRYPT ->
                "m = ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)}."
            NarrationId.RSA_STEP_ROUND_TRIP ->
                "${arg(0)} → ${arg(1)} → ${arg(2)}."
            NarrationId.RSA_STEP_SECRET_KEY -> "One of them has to stay secret."
            NarrationId.RSA_STEP_REAL_WORLD -> "And this is the toy version."

            // -- Exercise 1 · which kind of cryptography ------------------------
            NarrationId.RSA_ASK_ASYMMETRIC ->
                "Which kind of cryptography uses a public key and a private key?"
            NarrationId.RSA_HINT_ASYMMETRIC ->
                "One of these uses two different keys. The others use one, or none."
            NarrationId.RSA_RETRY_LOOK_ASYMMETRIC ->
                "Read the four again — only one of them mentions two keys."
            NarrationId.RSA_RETRY_ASK_ASYMMETRIC ->
                "If the same key both locks and unlocks, everyone who can read your " +
                    "messages can also send them. Which kind avoids that?"
            NarrationId.RSA_RETRY_EXPLAIN_ASYMMETRIC ->
                "Asymmetric cryptography uses a pair: a public key and a private " +
                    "key, mathematically related, and what one does the other undoes."
            NarrationId.RSA_WHY_NOT_SYMMETRIC ->
                "Not quite — symmetric cryptography uses **one** shared key for both " +
                    "directions. AES is the example you have already met. Its open " +
                    "question is how two people agree on that key, and RSA is an " +
                    "answer to it."
            NarrationId.RSA_WHY_NOT_HASHING ->
                "Not quite. Hashing has no key at all and nothing to undo — that is " +
                    "what the SHA-256 lesson is about."
            NarrationId.RSA_WHY_NOT_COMPRESSION ->
                "Not quite. Compression makes data smaller; it is not a security " +
                    "operation and has no keys."
            NarrationId.RSA_CORRECT_ASYMMETRIC ->
                "Correct. Asymmetric cryptography uses two related keys — and that " +
                    "is what lets one of them be published."

            // -- Exercise 2 · n = p × q -----------------------------------------
            NarrationId.RSA_ASK_MODULUS -> "What is n?"
            NarrationId.RSA_HINT_MODULUS -> "n is ${arg(0)} and ${arg(1)}, multiplied."
            NarrationId.RSA_RETRY_LOOK_MODULUS ->
                "Look at the formula on the row: n = p × q."
            NarrationId.RSA_RETRY_ASK_MODULUS -> "What is ${arg(0)} × ${arg(1)}?"
            NarrationId.RSA_RETRY_EXPLAIN_MODULUS ->
                "n = ${arg(0)} × ${arg(1)} = ${arg(2)}. It is the modulus, and both " +
                    "keys will carry it."
            NarrationId.RSA_WHY_N_IS_TOTIENT ->
                "That is φ(n), which comes next and is a different number. n is the " +
                    "two primes multiplied, not the two primes minus one each."
            NarrationId.RSA_WHY_N_IS_SUM ->
                "That is p + q. RSA multiplies the primes rather than adding them — " +
                    "which is the whole reason n is hard to take apart again."
            NarrationId.RSA_WHY_N_IS_PARTIAL ->
                "That subtracts 1 from one of the primes. n uses both of them as " +
                    "they are: ${arg(0)} × ${arg(1)}."
            NarrationId.RSA_CORRECT_MODULUS ->
                "Correct. n = ${arg(0)} × ${arg(1)} = ${arg(2)}, and both keys share it."

            // -- Exercise 3 · φ(n) = (p − 1)(q − 1) -----------------------------
            NarrationId.RSA_ASK_TOTIENT -> "What is φ(n)?"
            NarrationId.RSA_HINT_TOTIENT ->
                "Take 1 off each prime first, then multiply."
            NarrationId.RSA_RETRY_LOOK_TOTIENT ->
                "Look at the formula on the row: φ(n) = (p − 1)(q − 1)."
            NarrationId.RSA_RETRY_ASK_TOTIENT -> "What is ${arg(0)} × ${arg(1)}?"
            NarrationId.RSA_RETRY_EXPLAIN_TOTIENT ->
                "φ(n) = ${arg(0)} × ${arg(1)} = ${arg(2)}. It is the modulus that e " +
                    "and d are inverses in — not the one the message is encrypted in."
            NarrationId.RSA_WHY_PHI_IS_N ->
                "That is n. φ(n) takes 1 off each prime before multiplying, so it is " +
                    "always the smaller of the two."
            NarrationId.RSA_WHY_PHI_IS_PARTIAL ->
                "That takes 1 off only one of them. Both primes lose 1: " +
                    "${arg(0)} × ${arg(1)}."
            NarrationId.RSA_WHY_PHI_IS_HALF ->
                "That is one of the two factors on its own. They have to be " +
                    "multiplied together."
            NarrationId.RSA_CORRECT_TOTIENT ->
                "Correct. φ(n) = ${arg(0)} × ${arg(1)} = ${arg(2)} — and this is the " +
                    "number d will be built against."

            // -- Exercise 4 · a legal public exponent ---------------------------
            NarrationId.RSA_ASK_PUBLIC_EXPONENT ->
                "φ(n) is ${arg(0)}. Which of these can be the public exponent e?"
            NarrationId.RSA_HINT_PUBLIC_EXPONENT ->
                "e has to share no factor with ${arg(0)} — that is what " +
                    "gcd(e, φ(n)) = 1 means."
            NarrationId.RSA_RETRY_LOOK_PUBLIC_EXPONENT ->
                "Look at the condition on the row: gcd(e, φ(n)) = 1."
            NarrationId.RSA_RETRY_ASK_PUBLIC_EXPONENT ->
                "Which of these divides into ${arg(0)} evenly — and which does not?"
            NarrationId.RSA_RETRY_EXPLAIN_PUBLIC_EXPONENT ->
                "${arg(0)} is the only one here that shares no factor with ${arg(1)}. " +
                    "Without that, no d would exist for it."
            NarrationId.RSA_WHY_E_SHARES_FACTOR ->
                "${arg(0)} and ${arg(1)} share a factor of ${arg(2)}, so " +
                    "gcd is not 1 — and there is no d that would undo it."
            NarrationId.RSA_CORRECT_PUBLIC_EXPONENT ->
                "Correct. gcd(${arg(0)}, ${arg(1)}) = 1, so ${arg(0)} has an inverse " +
                    "and can be the public exponent."

            // -- Exercise 5 · the private exponent ------------------------------
            NarrationId.RSA_ASK_PRIVATE_EXPONENT ->
                "e is ${arg(0)} and φ(n) is ${arg(1)}. Which value is d?"
            NarrationId.RSA_HINT_PRIVATE_EXPONENT ->
                "Multiply each one by ${arg(0)}, then take the remainder mod " +
                    "${arg(1)}. You are looking for 1."
            NarrationId.RSA_RETRY_LOOK_PRIVATE_EXPONENT ->
                "Look at the condition on the row: d × e ≡ 1 (mod φ(n))."
            NarrationId.RSA_RETRY_ASK_PRIVATE_EXPONENT ->
                "Which of these, multiplied by ${arg(0)}, leaves a remainder of 1 " +
                    "when divided by ${arg(1)}?"
            NarrationId.RSA_RETRY_EXPLAIN_PRIVATE_EXPONENT ->
                "${arg(0)} × ${arg(1)} = ${arg(2)}, and ${arg(2)} leaves 1 when " +
                    "divided by ${arg(3)}. So d is ${arg(1)}."
            NarrationId.RSA_WHY_D_IS_E ->
                "That is e. d is the number that undoes it — a different value, " +
                    "found from e and φ(n) together."
            NarrationId.RSA_WHY_D_NOT_INVERSE ->
                "${arg(0)} × ${arg(1)} leaves ${arg(2)} when divided by ${arg(3)}, " +
                    "not 1 — so it would not undo the encryption."
            NarrationId.RSA_CORRECT_PRIVATE_EXPONENT ->
                "Correct. ${arg(0)} × ${arg(1)} = ${arg(2)}, which is 1 more than a " +
                    "multiple of ${arg(3)}. That is what makes d undo e."

            // -- Exercises 6 and 7 · the two keys -------------------------------
            NarrationId.RSA_ASK_PUBLIC_KEY -> "Which pair is the public key?"
            NarrationId.RSA_HINT_PUBLIC_KEY ->
                "The public key is the exponent you publish, with the modulus."
            NarrationId.RSA_RETRY_LOOK_PUBLIC_KEY ->
                "Look at the chain again — which exponent was the public one?"
            NarrationId.RSA_RETRY_ASK_PUBLIC_KEY ->
                "A key is written (exponent, modulus). Which exponent is public, and " +
                    "which of these numbers is the modulus?"
            NarrationId.RSA_RETRY_EXPLAIN_PUBLIC_KEY ->
                "The public key is (e, n) — here (${arg(0)}, ${arg(1)})."
            NarrationId.RSA_CORRECT_PUBLIC_KEY ->
                "Correct. The public key is (e, n) = (${arg(0)}, ${arg(1)}), and it " +
                    "is the half you can hand out."
            NarrationId.RSA_ASK_PRIVATE_KEY -> "And which pair is the private key?"
            NarrationId.RSA_HINT_PRIVATE_KEY ->
                "Same modulus, the other exponent."
            NarrationId.RSA_RETRY_LOOK_PRIVATE_KEY ->
                "Look at the chain again — which exponent was the private one?"
            NarrationId.RSA_RETRY_ASK_PRIVATE_KEY ->
                "The two keys share the modulus. Which exponent belongs to the half " +
                    "you keep?"
            NarrationId.RSA_RETRY_EXPLAIN_PRIVATE_KEY ->
                "The private key is (d, n) — here (${arg(0)}, ${arg(1)})."
            NarrationId.RSA_CORRECT_PRIVATE_KEY ->
                "Correct. The private key is (d, n) = (${arg(0)}, ${arg(1)}) — the " +
                    "same modulus, and the exponent that undoes the other one."
            NarrationId.RSA_WHY_KEY_SWAPPED ->
                "Those are the right two numbers the wrong way round. A key is " +
                    "written (exponent, modulus), and ${arg(1)} is the exponent here."
            NarrationId.RSA_WHY_KEY_IS_PRIVATE ->
                "That is the private key — the exponent that decrypts. The public " +
                    "one uses e."
            NarrationId.RSA_WHY_KEY_IS_PUBLIC ->
                "That is the public key — the exponent that encrypts. The private " +
                    "one uses d."
            NarrationId.RSA_WHY_KEY_USES_PHI ->
                "That is φ(n), not n. φ(n) was only ever used to *find* d; it never " +
                    "goes into a key, and anyone who had it could work d out."

            // -- Exercise 8 · c = mᵉ mod n --------------------------------------
            NarrationId.RSA_ASK_ENCRYPT ->
                "m = ${arg(0)}, e = ${arg(1)}, n = ${arg(2)}. What is c = mᵉ mod n?"
            NarrationId.RSA_HINT_ENCRYPT ->
                "Raise the message to the public exponent, then take the remainder."
            NarrationId.RSA_RETRY_LOOK_ENCRYPT ->
                "Look at the formula again: c = mᵉ mod n. Both halves matter."
            NarrationId.RSA_RETRY_ASK_ENCRYPT ->
                "You need ${arg(0)} raised to ${arg(1)}, and then the remainder when " +
                    "that is divided by ${arg(2)}. What is left?"
            NarrationId.RSA_RETRY_EXPLAIN_ENCRYPT ->
                "Raise ${arg(0)} to the power ${arg(1)}, then take the remainder mod " +
                    "${arg(2)}. That gives ${arg(3)}."
            NarrationId.RSA_WHY_C_NO_MOD ->
                "That is ${arg(0)} to the power ${arg(1)} — ${arg(2)} — without the " +
                    "mod. A ciphertext always lands below n, so taking the remainder " +
                    "mod ${arg(3)} is what finishes it: ${arg(4)}."
            NarrationId.RSA_WHY_C_MULTIPLIED ->
                "That is ${arg(0)} × ${arg(1)}. RSA raises the message to the " +
                    "exponent rather than multiplying by it."
            NarrationId.RSA_WHY_C_USED_PHI ->
                "That reduces by φ(n) = ${arg(0)}. Encryption uses n = ${arg(1)} — " +
                    "φ(n) only ever appeared while d was being found."
            NarrationId.RSA_WHY_C_UNCHANGED ->
                "That is the message itself. Encrypting it has to change it."
            NarrationId.RSA_CORRECT_ENCRYPT ->
                "Correct. ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)}. That is the " +
                    "ciphertext, and it was made with the public key alone."

            // -- Exercise 9 · m = c^d mod n -------------------------------------
            NarrationId.RSA_ASK_DECRYPT ->
                "c = ${arg(0)}, d = ${arg(1)}, n = ${arg(2)}. What was the message?"
            NarrationId.RSA_HINT_DECRYPT ->
                "The same operation as encryption, with the other exponent."
            NarrationId.RSA_RETRY_LOOK_DECRYPT ->
                "Look at the formula: m = c^d mod n. Nothing new — just d instead of e."
            NarrationId.RSA_RETRY_ASK_DECRYPT ->
                "What did you start with, before any of this?"
            NarrationId.RSA_RETRY_EXPLAIN_DECRYPT ->
                "${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)} — the message, back " +
                    "again."
            NarrationId.RSA_WHY_M_IS_CIPHERTEXT ->
                "That is the ciphertext you started this step with. Decrypting has " +
                    "to give back something different."
            NarrationId.RSA_WHY_M_IS_EXPONENT ->
                "That is one of the exponents, not the message. The exponents are " +
                    "what you calculate *with*."
            NarrationId.RSA_CORRECT_DECRYPT ->
                "Correct. ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)} — the " +
                    "original message. The private key undid the public key's work."

            // -- Exercise 10 · which key stays secret ---------------------------
            NarrationId.RSA_ASK_SECRET_KEY -> "Which key must remain secret?"
            NarrationId.RSA_HINT_SECRET_KEY ->
                "One of them is meant to be handed out. The other is what undoes it."
            NarrationId.RSA_RETRY_LOOK_SECRET_KEY ->
                "Look at the two key cards again — one says share, one does not."
            NarrationId.RSA_RETRY_ASK_SECRET_KEY ->
                "If someone else had the key that decrypts, what would be left of " +
                    "the message?"
            NarrationId.RSA_RETRY_EXPLAIN_SECRET_KEY ->
                "The private key is the one that must be kept. The public key is " +
                    "meant to be published — that is the whole point of it."
            NarrationId.RSA_WHY_SECRET_PUBLIC ->
                "The public key is the half that is *meant* to be handed out. " +
                    "Keeping it secret would stop anyone sending you anything."
            NarrationId.RSA_WHY_SECRET_BOTH ->
                "If the public key were secret too, nobody could encrypt anything " +
                    "for you — and publishing it is what makes this worth doing."
            NarrationId.RSA_WHY_SECRET_NEITHER ->
                "If the private key were published, anyone could undo the " +
                    "encryption, and the pair would protect nothing."
            NarrationId.RSA_CORRECT_SECRET_KEY ->
                "Correct. The private key is kept; the public key is published. " +
                    "Because they are a pair, giving one away does not give away the " +
                    "other — as long as the numbers are large enough."

            // -- The story judgements, asked before any arithmetic --------------
            //
            // Every rung here names the two key cards on screen, because at this
            // point in the lesson there is no arithmetic to point at yet.

            NarrationId.RSA_ASK_SHAREABLE_KEY -> "Which of these two can you share?"
            NarrationId.RSA_HINT_SHAREABLE_KEY ->
                "One of them is called *public*. That is not a coincidence."
            NarrationId.RSA_RETRY_LOOK_SHAREABLE_KEY ->
                "Look at the two cards again, and at what each one is named."
            NarrationId.RSA_RETRY_ASK_SHAREABLE_KEY ->
                "If someone is going to send you a message, which half do they " +
                    "need to have?"
            NarrationId.RSA_RETRY_EXPLAIN_SHAREABLE_KEY ->
                "The public key, ${arg(0)}, is the half you hand out. Anyone who " +
                    "has it can encrypt something for you, and that is the whole " +
                    "point of publishing it."
            NarrationId.RSA_WHY_SHARE_PRIVATE ->
                "The private key is the half that undoes the encryption. Hand it " +
                    "out and anyone could read what was sent to you."
            NarrationId.RSA_WHY_SHARE_BOTH ->
                "Sharing both is the same as sharing the private one, and the " +
                    "pair would protect nothing."
            NarrationId.RSA_WHY_SHARE_NEITHER ->
                "Then nobody could encrypt anything for you. Publishing one half " +
                    "is what makes this worth doing."
            NarrationId.RSA_CORRECT_SHAREABLE_KEY ->
                "Correct. ${arg(0)} is the public key, and it is meant to be given " +
                    "away — printed on a website, handed to a stranger, anything."

            NarrationId.RSA_ASK_ENCRYPT_OPERATION ->
                "You want to send “${arg(0)}” so that only one person can read it. " +
                    "What does RSA do to it?"
            NarrationId.RSA_HINT_ENCRYPT_OPERATION ->
                "Something readable goes in. What has to come out for this to be " +
                    "worth doing?"
            NarrationId.RSA_RETRY_LOOK_ENCRYPT_OPERATION ->
                "Read the message again, and think about what has to happen to it " +
                    "before it is safe to send."
            NarrationId.RSA_RETRY_ASK_ENCRYPT_OPERATION ->
                "Only one of these makes a message unreadable to everyone except " +
                    "its intended reader. Which one?"
            NarrationId.RSA_RETRY_EXPLAIN_ENCRYPT_OPERATION ->
                "RSA encrypts. “${arg(0)}” goes in readable and comes out as " +
                    "something nobody can read without the right key."
            NarrationId.RSA_CORRECT_ENCRYPT_OPERATION ->
                "Correct. “${arg(0)}” is about to become unreadable to everyone " +
                    "except the person it is for."

            NarrationId.RSA_ASK_ENCRYPT_KEY ->
                "“${arg(0)}” is about to be encrypted. Which key does that?"
            NarrationId.RSA_HINT_ENCRYPT_KEY ->
                "The sender does not have the recipient's private key — nobody does " +
                    "but the recipient. So which half can they possibly use?"
            NarrationId.RSA_RETRY_LOOK_ENCRYPT_KEY ->
                "Look at the two cards. One of them is published; one never leaves."
            NarrationId.RSA_RETRY_ASK_ENCRYPT_KEY ->
                "A stranger wants to write to you. Which half of your pair do they " +
                    "have?"
            NarrationId.RSA_RETRY_EXPLAIN_ENCRYPT_KEY ->
                "The public key encrypts. That is the whole reason it is published: " +
                    "anyone can use it to write to you, and only you can read the result."
            NarrationId.RSA_CORRECT_ENCRYPT_KEY ->
                "Correct. The public key encrypts — which is why handing it out is " +
                    "safe, and useful."

            NarrationId.RSA_ASK_DECRYPT_KEY ->
                "${arg(0)} arrives, and has to become “${arg(1)}” again. Which key " +
                    "does that?"
            NarrationId.RSA_HINT_DECRYPT_KEY ->
                "The public key did the scrambling, and everyone has it. If it could " +
                    "also unscramble, what would have been the point?"
            NarrationId.RSA_RETRY_LOOK_DECRYPT_KEY ->
                "Look at what each card says it does."
            NarrationId.RSA_RETRY_ASK_DECRYPT_KEY ->
                "Everyone has the public key. Which half does only the recipient have?"
            NarrationId.RSA_RETRY_EXPLAIN_DECRYPT_KEY ->
                "The private key decrypts. Only the recipient has it, so only the " +
                    "recipient can read what was sent to them."
            NarrationId.RSA_CORRECT_DECRYPT_KEY ->
                "Correct. The private key turns it back into “${arg(0)}”, and nothing " +
                    "else can."

            NarrationId.RSA_WHY_ENCRYPT_WITH_PRIVATE ->
                "The sender does not have your private key — that is the point of it. " +
                    "They could not use it even if they wanted to."
            NarrationId.RSA_WHY_ENCRYPT_WITH_BOTH ->
                "Using both would mean the sender needed your private key, and then " +
                    "it would not be private."
            NarrationId.RSA_WHY_ENCRYPT_WITH_NEITHER ->
                "A fixed rule with no key is a cipher anyone can undo once they know " +
                    "the rule. That was the Caesar lesson."
            NarrationId.RSA_WHY_DECRYPT_WITH_PUBLIC ->
                "Everyone has the public key. If it could undo the encryption, " +
                    "everyone could read the message."
            NarrationId.RSA_WHY_DECRYPT_WITH_SAME ->
                "Applying the same key twice is what XOR does, and it is what makes " +
                    "XOR symmetric. RSA's two keys are different numbers."
            NarrationId.RSA_WHY_DECRYPT_IMPOSSIBLE ->
                "Then it would be a hash, not encryption. The whole point is that the " +
                    "right person can get the message back."

            NarrationId.RSA_ASK_DECRYPT_OPERATION ->
                "Now ${arg(0)} goes through the private key. What happens to it?"
            NarrationId.RSA_HINT_DECRYPT_OPERATION ->
                "The public key scrambled it. What would the other half of the " +
                    "pair be for?"
            NarrationId.RSA_RETRY_LOOK_DECRYPT_OPERATION ->
                "Look at what went in at the top — it is the ciphertext, not the " +
                    "message."
            NarrationId.RSA_RETRY_ASK_DECRYPT_OPERATION ->
                "If the public key encrypts, what is left for the private key to do?"
            NarrationId.RSA_RETRY_EXPLAIN_DECRYPT_OPERATION ->
                "The private key decrypts. ${arg(0)} goes in and ${arg(1)} — the " +
                    "original message — comes back out."
            NarrationId.RSA_CORRECT_DECRYPT_OPERATION ->
                "Correct. The private key decrypts, so ${arg(0)} is about to turn " +
                    "back into ${arg(1)}."

            // Shared by both operation judgements: the same two misconceptions.
            NarrationId.RSA_WHY_OP_DECRYPT_FIRST ->
                "There is nothing to decrypt yet — the message has not been " +
                    "encrypted. Decryption is what the *other* key does, afterwards."
            NarrationId.RSA_WHY_OP_ENCRYPT_AGAIN ->
                "Encrypting a second time would take it further away, not back. " +
                    "The private key is the half that undoes the first step."
            NarrationId.RSA_WHY_OP_HASH ->
                "Hashing has no key and no way back, so nothing could be recovered " +
                    "at the other end. That was the SHA-256 lesson."
            NarrationId.RSA_WHY_OP_COMPRESS ->
                "Compression makes data smaller and hides nothing. Anyone who " +
                    "receives it can read it straight back."
            NarrationId.RSA_WHY_OP_SORT ->
                "Sorting the characters would scramble the message, but anyone could " +
                    "unscramble it — and you could not get the original order back."

            NarrationId.RSA_ASK_KEY_PAIR_PURPOSE ->
                "So what does having this pair of keys let you do?"
            NarrationId.RSA_HINT_KEY_PAIR_PURPOSE ->
                "Look back at what each half did: one scrambled the message, the " +
                    "other brought it back."
            NarrationId.RSA_RETRY_LOOK_KEY_PAIR_PURPOSE ->
                "The two keys did two different jobs. Look at which did which."
            NarrationId.RSA_RETRY_ASK_KEY_PAIR_PURPOSE ->
                "One key was published and one was kept. What does that make " +
                    "possible that a single shared key does not?"
            NarrationId.RSA_RETRY_EXPLAIN_KEY_PAIR_PURPOSE ->
                "One key encrypts, the other decrypts. Because only one of them " +
                    "has to be kept, a stranger can send you something that only " +
                    "you can read — without the two of you ever agreeing a secret."
            NarrationId.RSA_CORRECT_KEY_PAIR_PURPOSE ->
                "Correct. ${arg(0)} became ${arg(1)} with one key and came back " +
                    "with the other. That is the whole of what asymmetric means."
            NarrationId.RSA_WHY_PURPOSE_SHARED ->
                "That is symmetric cryptography — one key both ways, which is what " +
                    "Caesar, XOR and AES do. RSA's two keys are different numbers."
            NarrationId.RSA_WHY_PURPOSE_INTEGRITY ->
                "Detecting a change is a hash's job, and a hash hides nothing. " +
                    "Here the message was genuinely unreadable in between."
            NarrationId.RSA_WHY_PURPOSE_STORAGE ->
                "Storing something irreversibly is hashing again. This message " +
                    "came back — that is the opposite of irreversible."

            // -- WATCH -----------------------------------------------------------
            // The opening states the *problem*, not the answer — the answer is what
            // the next two beats are for (ADR-052).
            NarrationId.RSA_WATCH_SETUP -> "Two strangers need to agree a secret."
            NarrationId.RSA_WATCH_SETUP_SUPPORT ->
                "Caesar, XOR and AES all share one key between both sides, which " +
                    "leaves a question none of them answers: how do two people who " +
                    "have never met agree on it?"
            // -- Layer 1: the concept, told on a message, with no numbers -------
            NarrationId.RSA_WATCH_MESSAGE -> "Let's send a secret message."
            NarrationId.RSA_WATCH_MESSAGE_SUPPORT ->
                "“${arg(0)}” — something you would not want read on the way. " +
                    "Everything in this lesson happens to this message."
            NarrationId.RSA_WATCH_ENCRYPT_OPERATION -> "RSA encrypts it."
            NarrationId.RSA_WATCH_ENCRYPT_OPERATION_SUPPORT ->
                "Readable goes in; something nobody else can read comes out. That " +
                    "is the whole job."
            NarrationId.RSA_WATCH_KEY_REVEAL -> "RSA uses two related keys."
            NarrationId.RSA_WATCH_KEY_REVEAL_SUPPORT ->
                "One you can hand to anyone, one you never share. Where they come " +
                    "from is the second half of this lesson; what they do is this half."
            NarrationId.RSA_WATCH_ASYMMETRIC -> "This is asymmetric cryptography."
            NarrationId.RSA_WATCH_ASYMMETRIC_SUPPORT ->
                "Two different keys, mathematically related. What one does, the " +
                    "other undoes — which is why one of them can be published."
            NarrationId.RSA_WATCH_ENCRYPT_KEY ->
                "The sender uses the recipient's public key."
            NarrationId.RSA_WATCH_ENCRYPT_KEY_SUPPORT ->
                "They do not need to know the recipient, meet them, or agree " +
                    "anything with them first. The public key is already out there."
            NarrationId.RSA_WATCH_CIPHERTEXT -> "Now it reads ${arg(0)}."
            NarrationId.RSA_WATCH_CIPHERTEXT_SUPPORT ->
                "This can be sent over anything. Intercept it and you have bytes " +
                    "and no way to get the message out of them."
            NarrationId.RSA_WATCH_DECRYPT_KEY ->
                "The recipient uses their private key."
            NarrationId.RSA_WATCH_DECRYPT_KEY_SUPPORT ->
                "The half that never left their device. Nobody else has it, so " +
                    "nobody else can do this."
            NarrationId.RSA_WATCH_RECOVERED -> "“${arg(0)}”, back again."
            NarrationId.RSA_WATCH_RECOVERED_SUPPORT ->
                "The message arrived, and at no point did the two of them share a " +
                    "secret. That is what the other ciphers could not do."
            NarrationId.RSA_WATCH_ROUND_TRIP -> "That is RSA, end to end."
            NarrationId.RSA_WATCH_ROUND_TRIP_SUPPORT ->
                "“${arg(0)}” out through one key and back through the other. " +
                    "Everything after this is *how*."

            // -- The bridge: text becomes numbers, and the toy admits it is one --
            NarrationId.RSA_WATCH_TEXT_AS_NUMBERS -> "Computers store text as numbers."
            NarrationId.RSA_WATCH_TEXT_AS_NUMBERS_SUPPORT ->
                "M is 77, E is 69, and so on. That is what gives RSA something to " +
                    "do arithmetic on — it never sees letters."
            NarrationId.RSA_WATCH_TOY_EXAMPLE -> "Now a toy example."
            NarrationId.RSA_WATCH_TOY_EXAMPLE_SUPPORT ->
                "From here the lesson uses one tiny number, m = ${arg(0)}, so every " +
                    "step can be checked by hand. It is the mechanism — **not** " +
                    "“${arg(1)}” being encrypted, which needs numbers far larger " +
                    "than these."

            NarrationId.RSA_WATCH_TOY_CIPHERTEXT -> "${arg(0)} is now ${arg(1)}."
            NarrationId.RSA_WATCH_TOY_CIPHERTEXT_SUPPORT ->
                "One number in, a different number out — and nothing about it says " +
                    "what it started as. Now the other key has to undo it."

            // -- Layer 2: the mechanism, and where the keys came from -----------
            NarrationId.RSA_WATCH_SHAREABLE_KEY ->
                "The public key is the one you publish."
            NarrationId.RSA_WATCH_SHAREABLE_KEY_SUPPORT ->
                "Print it, email it, hand it to a stranger. Giving it away is what " +
                    "it is for."
            NarrationId.RSA_WATCH_DECRYPT_OPERATION ->
                "The private key decrypts ${arg(0)}."
            NarrationId.RSA_WATCH_DECRYPT_OPERATION_SUPPORT ->
                "The other half of the pair, doing the opposite job. This is the " +
                    "half that never leaves your device."
            NarrationId.RSA_WATCH_CLOSING_FLOW -> "And that is the whole journey."
            NarrationId.RSA_WATCH_CLOSING_FLOW_SUPPORT ->
                "“${arg(0)}” out through the public key, back through the private " +
                    "one — and now you know what every arrow on it is doing."
            NarrationId.RSA_WATCH_KEY_ORIGIN ->
                "So where did ${arg(0)} and ${arg(1)} come from?"
            NarrationId.RSA_WATCH_KEY_ORIGIN_SUPPORT ->
                "You have watched them work. Now here is how they were built — " +
                    "five numbers, each one worked out from the ones before it."
            NarrationId.RSA_WATCH_KEY_PAIR_PURPOSE ->
                "Encrypt with one, decrypt with the other."
            NarrationId.RSA_WATCH_KEY_PAIR_PURPOSE_SUPPORT ->
                "Because only one half has to be kept, a stranger can send you " +
                    "something only you can read — with no shared secret between you."

            // -- The line that introduces the cards, when one is pending --------
            NarrationId.RSA_WATCH_ASK_ASYMMETRIC ->
                "RSA solves that with two related keys instead of one shared one. " +
                    "What is that called?"
            NarrationId.RSA_WATCH_ASK_SHAREABLE_KEY ->
                "They are a pair with opposite jobs. Which one can you hand out?"
            NarrationId.RSA_WATCH_ASK_SECRET_KEY ->
                "So which half must never leave?"
            NarrationId.RSA_WATCH_ASK_ENCRYPT_OPERATION ->
                "You want to send it without anyone else reading it. What does RSA " +
                    "do to it?"
            NarrationId.RSA_WATCH_ASK_DECRYPT_OPERATION ->
                "Now send it through the private key instead. What happens?"
            NarrationId.RSA_WATCH_ASK_ENCRYPT_KEY ->
                "The sender has to pick one of them. Which key encrypts?"
            NarrationId.RSA_WATCH_ASK_DECRYPT_KEY ->
                "And at the other end — which key turns it back?"
            NarrationId.RSA_WATCH_ASK_KEY_PAIR_PURPOSE ->
                "One last question: what does having the pair let you do?"

            NarrationId.RSA_WATCH_PRIMES -> "Start with two primes: ${arg(0)} and ${arg(1)}."
            NarrationId.RSA_WATCH_PRIMES_SUPPORT ->
                "These are the only secret inputs. Everything else on this screen is " +
                    "worked out from them."
            NarrationId.RSA_WATCH_MODULUS -> "n = ${arg(0)} × ${arg(1)} = ${arg(2)}."
            NarrationId.RSA_WATCH_MODULUS_SUPPORT ->
                "The modulus. Both keys will carry it, and every calculation from " +
                    "here happens mod n."
            NarrationId.RSA_WATCH_TOTIENT ->
                "φ(n) = ${arg(0)} × ${arg(1)} = ${arg(2)}."
            NarrationId.RSA_WATCH_TOTIENT_SUPPORT ->
                "Take 1 off each prime, then multiply. This one never goes into a " +
                    "key — it exists to find d, and then it is thrown away."
            NarrationId.RSA_WATCH_PUBLIC_EXPONENT -> "Choose e = ${arg(0)}."
            NarrationId.RSA_WATCH_PUBLIC_EXPONENT_SUPPORT ->
                "It only has to share no factor with φ(n): gcd(${arg(0)}, ${arg(1)}) " +
                    "= 1. That is what guarantees a d exists to undo it."
            NarrationId.RSA_WATCH_PRIVATE_EXPONENT -> "And d = ${arg(0)}."
            NarrationId.RSA_WATCH_PRIVATE_EXPONENT_SUPPORT ->
                "${arg(0)} × ${arg(1)} = ${arg(2)}, which is 1 more than a multiple " +
                    "of ${arg(3)}. That relationship is the whole reason one key " +
                    "undoes the other."
            // Act II: the pair the learner has been using all along, now assembled
            // out of the chain they have just derived.
            NarrationId.RSA_WATCH_PUBLIC_KEY -> "That gives the public key: ${arg(0)}."
            NarrationId.RSA_WATCH_PUBLIC_KEY_SUPPORT ->
                "The exponent that encrypts, and the modulus. The same pair that " +
                    "scrambled the message at the start of the lesson."
            NarrationId.RSA_WATCH_PRIVATE_KEY -> "And the private key: ${arg(0)}."
            NarrationId.RSA_WATCH_PRIVATE_KEY_SUPPORT ->
                "The same modulus, ${arg(0)}, and the other exponent. Two keys, one " +
                    "shared number, and only one of them ever leaves."
            NarrationId.RSA_WATCH_ENCRYPT ->
                "c = ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)}."
            NarrationId.RSA_WATCH_ENCRYPT_SUPPORT ->
                "That is what the public key ${arg(0)} was doing: raise the message " +
                    "to its exponent, then take the remainder."
            NarrationId.RSA_WATCH_DECRYPT ->
                "m = ${arg(0)}^${arg(1)} mod ${arg(2)} = ${arg(3)}."
            NarrationId.RSA_WATCH_DECRYPT_SUPPORT ->
                "The private key ${arg(0)} does the same thing with the other " +
                    "exponent — and the message is back. One operation, two keys."
            NarrationId.RSA_WATCH_SECRET_KEY -> "Only one of them has to be kept."
            NarrationId.RSA_WATCH_SECRET_KEY_SUPPORT ->
                "The public key is meant to be handed out. The private key is what " +
                    "undoes its work, so it never leaves — and because they are a " +
                    "pair, publishing one does not give away the other."
            NarrationId.RSA_WATCH_REAL_WORLD -> "And this is the toy version."
            NarrationId.RSA_WATCH_REAL_WORLD_SUPPORT ->
                "n = ${arg(0)} factors by inspection, so anyone with the public key " +
                    "here has the private one too. Real RSA uses numbers hundreds of " +
                    "digits long, and adds a padding scheme — OAEP — because " +
                    "textbook RSA on its own is deterministic and not safe to use."

            NarrationId.RSA_WATCH_INSIGHT ->
                "Two keys, built from the same chain — and only one has to be kept."
            NarrationId.RSA_WATCH_INSIGHT_SUPPORT ->
                "That is what makes it asymmetric, and it is why a stranger can " +
                    "encrypt something only you can read."
            NarrationId.RSA_WATCH_SUMMARY -> "One key out, one key in."
            NarrationId.RSA_WATCH_SUMMARY_SUPPORT -> "The idea"

            NarrationId.RSA_IDEA_1 ->
                "RSA is asymmetric: a public key and a private key, mathematically " +
                    "related, where what one does the other undoes."
            NarrationId.RSA_IDEA_2 ->
                "Key generation starts from two primes. n = p × q — here " +
                    "${arg(0)} × ${arg(1)} = ${arg(2)} — and φ(n) = (p − 1)(q − 1)."
            NarrationId.RSA_IDEA_3 ->
                "e is chosen with gcd(e, φ(n)) = 1, and d is the value with " +
                    "d × e ≡ 1 (mod φ(n)). The public key is (e, n) and the private " +
                    "key is (d, n)."
            NarrationId.RSA_IDEA_4 ->
                "Encryption is c = mᵉ mod n and decryption is m = c^d mod n — the " +
                    "same modular exponentiation, with the other exponent."
            NarrationId.RSA_IDEA_5 ->
                "That exponentiation is far more expensive than a symmetric cipher, " +
                    "which is why real systems commonly use asymmetric cryptography " +
                    "to agree on a key and a symmetric algorithm like AES for the " +
                    "data itself."
            // The two a learner must not leave without, last, where a recap bullet
            // is read rather than skipped.
            NarrationId.RSA_IDEA_6 ->
                "These numbers are a demonstration, not security. n = 55 factors by " +
                    "inspection. Real RSA uses keys of 2048 bits or more, where " +
                    "factoring n is what nobody knows how to do quickly."
            NarrationId.RSA_IDEA_7 ->
                "Textbook RSA — the version here, with no padding — is deterministic " +
                    "and should not be used as it stands. Real encryption adds a " +
                    "padding scheme such as OAEP, and in practice you use a reviewed " +
                    "library rather than writing any of this yourself."
        }
    }
}
