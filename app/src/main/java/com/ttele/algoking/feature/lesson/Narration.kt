package com.ttele.algoking.feature.lesson

import com.ttele.algoking.engine.narration.NarrationId
import com.ttele.algoking.engine.narration.NarrationKey

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
        }
    }
}
