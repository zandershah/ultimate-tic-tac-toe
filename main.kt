import java.util.*
import kotlin.io.*
import kotlin.math.*

val LOW_MASK = 0b111111111

val winning_memo = run {
    val winning: MutableList<Int> = mutableListOf()

    // Diagonals.
    winning.add((1 shl 0) + (1 shl 4) + (1 shl 8))
    winning.add((1 shl 2) + (1 shl 4) + (1 shl 6))

    for (i in 0 .. 2) {
        winning.add(0b111 shl (i * 3))
        winning.add((1 shl i) + (1 shl (i + 3)) + (1 shl (i + 6)))
    }

    BooleanArray(1 shl 9, { i -> winning.any { (it and i) == it }})
}

val move_memo = Array(1 shl 9, { i -> (0 until 9).filter {
    ((i shr it) and 1) == 0 }.map { it.toByte() }.toByteArray()
})

var leftover = (0 until 81).map { it.toByte() }

data class State(
    val board: LongArray = LongArray(3, { 0L }),
    var last_move: Byte = 0,
    var player: Byte = 1,
    var large_board: Int = 0
) {
    var winning = false

    fun deep_copy(
        board: LongArray = this.board.copyOf(),
        last_move: Byte = this.last_move,
        player: Byte = this.player,
        large_board: Int = this.large_board
    ) = State(board, last_move, player, large_board)

    fun apply(move: Byte) {
        val small_board = move / 27 * 3 + move % 9 / 3
        val bitwise_move = 1L shl (move / 9 % 3 * 3 + move % 3 + 9 * player + small_board % 3 * 18)
        board[small_board / 3] = board[small_board / 3] or bitwise_move

        if (winning_memo[(board[small_board / 3] shr (small_board % 3 * 18)).toInt() and LOW_MASK] || winning_memo[(board[small_board / 3] shr (9 + small_board % 3 * 18)).toInt() and LOW_MASK]) {
            large_board = large_board or (1 shl (small_board + 9 * player))
            winning = winning_memo[large_board and LOW_MASK] || winning_memo[large_board shr 9]
        }

        last_move = move
        player = (1 - player).toByte()
    }

    fun move(): Byte {
        val small_row = last_move / 9 % 3 * 3
        val small_col = last_move % 3 * 3
        var small_board = small_row + small_col / 3
        var board_index = small_board / 3
        var board_offset = small_board % 3 * 18
        val bitwise_board = ((board[board_index] shr board_offset).toInt() and LOW_MASK) or ((board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK)

        if (move_memo[bitwise_board].isEmpty() || winning_memo[(board[board_index] shr board_offset).toInt() and LOW_MASK] || winning_memo[(board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]) {
            repeat(5) {
                val move = leftover.random()
                small_board = move / 27 * 3 + move % 9 / 3
                board_index = small_board / 3
                board_offset = small_board % 3 * 18
                val bitwise_move = move / 9 % 3 * 3 + move % 3
                val empty = (((((board[board_index] shr board_offset).toInt() and LOW_MASK) or ((board[board_index] shr (9 + board_offset)).toInt())) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[(board[board_index] shr board_offset).toInt() and LOW_MASK] && !winning_memo[(board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]

                if (empty && unfinished) return move
            }

            leftover.shuffled().forEach {
                small_board = it / 27 * 3 + it % 9 / 3
                board_index = small_board / 3
                board_offset = small_board % 3 * 18
                val bitwise_move = it / 9 % 3 * 3 + it % 3
                val empty = (((((board[board_index] shr board_offset).toInt() and LOW_MASK) or ((board[board_index] shr (9 + board_offset)).toInt())) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[(board[board_index] shr board_offset).toInt() and LOW_MASK] && !winning_memo[(board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]
                if (empty && unfinished) return it
            }

            return (-1).toByte()
        } else {
            val move = move_memo[bitwise_board].random()
            return ((move / 3 + small_row) * 9 + move % 3 + small_col).toByte()
        }
    }

    fun moves(): List<Byte> {
        val small_row = last_move / 9 % 3 * 3
        val small_col = last_move % 3 * 3
        var small_board = small_row + small_col / 3
        var board_index = small_board / 3
        var board_offset = small_board % 3 * 18
        val bitwise_board = ((board[board_index] shr board_offset).toInt() and LOW_MASK) or ((board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK)

        return if (move_memo[bitwise_board].isEmpty() || winning_memo[(board[board_index] shr board_offset).toInt() and LOW_MASK] || winning_memo[(board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]) {
            leftover.filter {
                small_board = it / 27 * 3 + it % 9 / 3
                board_index = small_board / 3
                board_offset = small_board % 3 * 18
                val bitwise_move = it / 9 % 3 * 3 + it % 3
                val empty = (((((board[board_index] shr board_offset).toInt() and LOW_MASK) or ((board[board_index] shr (9 + board_offset)).toInt())) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[(board[board_index] shr board_offset).toInt() and LOW_MASK] && !winning_memo[(board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]
                empty && unfinished
            }
        } else {
            move_memo[bitwise_board].map { it -> ((it / 3 + small_row) * 9 + it % 3 + small_col).toByte() }
        }
    }
}

class Tree {
    class Node(
        var parent: Node? = null,
        val move: Byte = 0
    ) {
        var children: Array<Node>? = null
        var visits = 0
        var value = 0

        fun leaf() = children == null || children!!.isEmpty()

        fun expand(state: State) : Node {
            if (state.winning) return this

            val moves = state.moves()
            children = Array(moves.size, { Node(this, moves[it]) })

            return if (children!!.isEmpty()) this else children!!.last()
        }
    }

    var root = Node()
    var root_state = State()
    var total_visits = 0

    fun apply(move: Byte) {
        root = root.children?.find { it.move == move } ?: Node(move=move)
        root.parent = null

        total_visits = root.visits

        root_state.apply(move)
    }

    // TODO: Not efficient?
    fun selection(): Pair<Node, State> {
        var node = root
        val state = root_state.deep_copy()

        // TODO: Set exploration parameter.
        val c = sqrt(1.0 * ln(total_visits.toDouble()))
        while (!node.leaf()) {
            node = node.children!!.maxBy {
                if (it.visits == 0) {
                    Double.MAX_VALUE
                } else {
                    it.value.toDouble() / it.visits + c / sqrt(it.visits.toDouble())
                }
            }!!
            state.apply(node.move)
        }

        return Pair(node, state)
    }

    // TODO: Heuristic: Never take a non-winning opponent free-move over a winning opponent free-move.
    fun simulate(state: State) : Pair<Int, Boolean> {
        val player = state.player
        var terminal = true

        while (!state.winning) {
            val move = state.move()
            if (move == (-1).toByte()) {
                // Tie-breaker.
                val wins = arrayOf(
                    state.board.sumBy { i -> (0 until 3).sumBy { if (winning_memo[(i shr (18 * it)).toInt() and LOW_MASK]) 1 else 0 } },
                    state.board.sumBy { i -> (0 until 3).sumBy { if (winning_memo[(i shr (9 + 18 * it)).toInt() and LOW_MASK]) 1 else 0 } }
                )
                return Pair((wins[1 - player.toInt()] - wins[player.toInt()]).sign, terminal)
            }

            state.apply(move)
            terminal = false
        }

        val winner = if (winning_memo[state.large_board and LOW_MASK]) 0.toByte() else 1.toByte()
        return Pair(if (player == winner) -1 else 1, terminal)
    }

    fun backpropagate(leaf: Node, result: Pair<Int, Boolean>) {
        var node: Node? = leaf
        var (score, terminal_state) = result

        while (node != null) {
            ++node.visits

            if (node.leaf() && terminal_state) {
                node.value = Int.MAX_VALUE * score
            } else if (!node.leaf() && node.children!!.all { it.value < Int.MIN_VALUE / 2 }) {
                node.value = Int.MAX_VALUE
            } else if (!node.leaf() && node.children!!.any { it.value > Int.MAX_VALUE / 2 }) {
                node.value = Int.MIN_VALUE
            } else {
                node.value = node.value + score
            }

            score = -score
            node = node.parent
        }
    }

    fun mcts(duration: Long): Byte {
        val start = System.currentTimeMillis()

        leftover = leftover.filter {
            val small_board = it / 27 * 3 + it % 9 / 3
            val board_index = small_board / 3
            val board_offset = small_board % 3 * 18
            val bitwise_move = it / 9 % 3 * 3 + it % 3
            val empty = (((((root_state.board[board_index] shr board_offset).toInt() and LOW_MASK) or ((root_state.board[board_index] shr (9 + board_offset)).toInt())) shr bitwise_move) and 1) == 0
            val unfinished = !winning_memo[(root_state.board[board_index] shr board_offset).toInt() and LOW_MASK] && !winning_memo[(root_state.board[board_index] shr (9 + board_offset)).toInt() and LOW_MASK]
            empty && unfinished
        }.map { it.toByte() }

        while(System.currentTimeMillis() - start < duration - 3) {
            val (node, state) = selection()

            val leaf = node.expand(state)
            if (leaf != node) state.apply(leaf.move)

            val result = simulate(state)
            backpropagate(leaf, result)

            ++total_visits
        }

        System.err.println("Visits: ${total_visits}")
        val node = root.children!!.maxBy { it.visits }!!
        System.err.println(node.value)
        return node.move
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    var tree = Tree()
    var turn = 0

    // First turn.
    while (true) {
        val row = scanner.nextByte()
        val col = scanner.nextByte()

        val start = System.currentTimeMillis()

        if (turn != 0 && turn % 4 == 0) System.gc()

        tree.apply(if (turn == 0 && row == (-1).toByte()) (4 * 9 + 4).toByte() else (row * 9 + col).toByte())

        repeat(scanner.nextInt()) {
            scanner.nextByte()
            scanner.nextByte()
        }

        val duration = (if (turn == 0) 1000L else 100L) - (System.currentTimeMillis() - start)
        System.err.println("Duration: ${duration}")

        val move = tree.mcts(duration)

        if (turn == 0 && row == (-1).toByte()) {
            println("4 4")
        } else {
            println("${move / 9} ${move % 9}")
            tree.apply(move)
        }

        ++turn
    }
}