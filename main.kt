import java.util.*
import kotlin.io.*
import kotlin.math.*

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
    val board: Array<IntArray> = Array(2, { IntArray(9, { 0 }) }),
    var last_move: Byte = 0,
    var player: Int = 0
) {
    var winning = false

    fun deep_copy(
        board: Array<IntArray> = arrayOf(this.board[0].copyOf(), this.board[1].copyOf()),
        last_move: Byte = this.last_move,
        player: Int = this.player
    ) = State(board, last_move, player)

    fun apply(move: Byte) {
        val small_board = move / 27 * 3 + move % 9 / 3
        val bitwise_move = 1 shl (move / 9 % 3 * 3 + move % 3)
        board[player][small_board] = board[player][small_board] or bitwise_move

        last_move = move
        player = 1 - player

        val large_board = board.map { it.reversed().fold(0) { acc, i -> (acc * 2) + (if (winning_memo[i]) 1 else 0) } }
        winning = winning_memo[large_board[0]] || winning_memo[large_board[1]]
    }

    fun move(): Byte {
        val small_row = last_move / 9 % 3 * 3
        val small_col = last_move % 3 * 3
        var small_board = small_row + small_col / 3
        val bitwise_board = board[0][small_board] or board[1][small_board]

        if (move_memo[bitwise_board].isEmpty() || winning_memo[board[0][small_board]] || winning_memo[board[1][small_board]]) {
            repeat(3) {
                val move = leftover.random()
                small_board = move / 27 * 3 + move % 9 / 3
                val bitwise_move = move / 9 % 3 * 3 + move % 3
                val empty = (((board[0][small_board] or board[1][small_board]) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]

                if (empty && unfinished) return move
            }

            leftover.shuffled().forEach {
                small_board = it / 27 * 3 + it % 9 / 3
                val bitwise_move = it / 9 % 3 * 3 + it % 3
                val empty = (((board[0][small_board] or board[1][small_board]) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]
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
        val bitwise_board = board[0][small_board] or board[1][small_board]

        return if (move_memo[bitwise_board].isEmpty() || winning_memo[board[0][small_board]] || winning_memo[board[1][small_board]]) {
            leftover.filter {
                small_board = it / 27 * 3 + it % 9 / 3
                val bitwise_move = it / 9 % 3 * 3 + it % 3
                val empty = (((board[0][small_board] or board[1][small_board]) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]
                empty && unfinished
            }
        } else {
            move_memo[bitwise_board].map { it -> ((it / 3 + small_row) * 9 + it % 3 + small_col).toByte() }
        }
    }
}

class Tree {
    inner class Node(
        var parent: Node? = null,
        val move: Byte = 0
    ) {
        var children: Array<Node>? = null
        var visits = 0
        var value = 0

        fun leaf() = children == null || children!!.isEmpty()

        fun expand(state: State) : Node {
            if (state.winning) return this

            children = state.moves().map { Node(this, it) }.toTypedArray()

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
        var terminal_state = true
        
        while (!state.winning) {
            val move = state.move()
            if (move == (-1).toByte()) {
                // Tie-breaker.
                val wins = state.board.map { it.sumBy { i -> if (winning_memo[i]) 1 else 0 } }
                return Pair((wins[player] - wins[1 - player]).sign, terminal_state)
            }

            state.apply(move)
            terminal_state = false
        }

        return Pair(if (state.player == player) 1 else -1, terminal_state)
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

    fun mcts(duration: Int) : Byte {
        val start = System.currentTimeMillis()
        leftover = (0 until 81).filter {
            val small_board = it / 27 * 3 + it % 9 / 3
            val bitwise_move = it / 9 % 3 * 3 + it % 3
            val empty = (((root_state.board[0][small_board] or root_state.board[1][small_board]) shr bitwise_move) and 1) == 0
            val unfinished = !winning_memo[root_state.board[0][small_board]] && !winning_memo[root_state.board[1][small_board]]
            empty && unfinished
        }.map { it.toByte() }

        while (System.currentTimeMillis() - start <= duration - 1) {
            val (node, state) = selection()

            val leaf = node.expand(state)
            if (node != leaf) state.apply(leaf.move)

            backpropagate(leaf, simulate(state))

            ++total_visits
        }

        System.err.println(total_visits)
        return root.children!!.maxBy { it.visits }!!.move
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    var tree = Tree()
    var first = true

    // First turn.
    while (true) {
        val row = scanner.nextByte()
        val col = scanner.nextByte()

        val start = System.currentTimeMillis()
        tree.apply(if (first && row == (-1).toByte()) (4 * 9 + 4).toByte() else (row * 9 + col).toByte())

        repeat(scanner.nextInt()) {
            scanner.nextInt()
            scanner.nextInt()
        }

        val duration = (if (first) 1000 else 100) - (System.currentTimeMillis() - start).toInt()
        val move = tree.mcts(duration)

        if (first && row == (-1).toByte()) {
            println("4 4")
        } else {
            tree.apply(move)
            println("${move / 9} ${move % 9}")
        }

        first = false
    }
}