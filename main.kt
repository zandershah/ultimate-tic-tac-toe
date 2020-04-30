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

    Array(1 shl 9, { i -> winning.any { (it and i) == it }})
}

// TODO: Keep set of remaining moves instead of iterating over all 81.

data class State(
    val board: Array<Array<Int>> = Array(2, { Array(9, { 0 }) }),
    var last_move: Pair<Byte, Byte> = Pair(0, 0),
    var player: Int = 0
) {
    fun deep_copy(
        board: Array<Array<Int>> = arrayOf(this.board[0].copyOf(), this.board[1].copyOf()),
        last_move: Pair<Byte, Byte> = this.last_move,
        player: Int = this.player
    ) = State(board, last_move, player)

    fun apply(move: Pair<Byte, Byte>) {
        val small_board = move.first / 3 * 3 + move.second / 3
        val bitwise_move = 1 shl (move.first % 3 * 3 + move.second % 3)
        board[player][small_board] = board[player][small_board] or bitwise_move

        last_move = move
        player = 1 - player
    }

    fun winning(): Boolean {
        val large_board = board.map { it.reversed().fold(0) { acc, i -> (acc shl 1) + (if (winning_memo[i]) 1 else 0) } }
        return winning_memo[large_board[0]] || winning_memo[large_board[1]]
    }

    fun move(): Pair<Byte, Byte>? {
        val small_row = last_move.first % 3 * 3
        val small_col = last_move.second % 3 * 3
        var small_board = small_row + small_col / 3

        if (!winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]) {
            val bitwise_board = board[0][small_board] or board[1][small_board]
            (0 until 9).shuffled().forEach {
                if (((bitwise_board shr it) and 1) == 0) {
                    return Pair((it / 3 + small_row).toByte(), (it % 3 + small_col).toByte())
                }
            }
        }

        // Move anywhere valid.
        (0 until 81).shuffled().forEach {
            small_board = it / 27 * 3 + it % 9 / 3
            val bitwise_move = it / 9 % 3 * 3 + it % 3
            val empty = (((board[0][small_board] or board[1][small_board]) shr bitwise_move) and 1) == 0
            val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]
            if (empty && unfinished) {
                return Pair((it / 9).toByte(), (it % 9).toByte())
            }
        }

        return null
    }

    fun moves(): List<Pair<Byte, Byte>> {
        val small_row = last_move.first % 3 * 3
        val small_col = last_move.second % 3 * 3
        var small_board = small_row + small_col / 3
        val bitwise_board = board[0][small_board] or board[1][small_board]

        var moves = (0 until 9).filter {
            ((bitwise_board shr it) and 1) == 0
        }.map { Pair (it / 3 + small_row, it % 3 + small_col)}

        // Move anywhere valid.
        if (moves.isEmpty() || winning_memo[board[0][small_board]] || winning_memo[board[1][small_board]]) {
            moves = (0 until 81).filter {
                small_board = it / 27 * 3 + it % 9 / 3
                val bitwise_move = it / 9 % 3 * 3 + it % 3
                val empty = (((board[0][small_board] or board[1][small_board]) shr bitwise_move) and 1) == 0
                val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]
                empty && unfinished
            }.map { Pair (it / 9, it % 9) }
        }

        return moves.map { Pair(it.first.toByte(), it.second.toByte()) }
    }
}

class Tree {
    inner class Node(
        val parent: Node? = null,
        val move: Pair<Byte, Byte> = Pair(0, 0)
    ) {
        var children: List<Node>? = null
        var visits = 0
        var value = 0

        fun leaf() = children == null || children!!.isEmpty()

        fun expand(state: State) : Node {
            if (state.winning()) return this

            children = state.moves().map { Node(this, it) }

            return if (children!!.isEmpty()) this else children!!.last()
        }
    }

    var root = Node()
    var root_state = State()
    var total_visits = 0

    fun apply(move: Pair<Byte, Byte>) {
        root = root.children?.find { it.move == move } ?: Node(move=move)
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

    // TODO: Heuristic simulation.
    fun simulate(state: State) : Int {
        val player = state.player

        while (!state.winning()) {
            val move = state.move()
            if (move == null) {
                // Tie-breaker.
                val wins = state.board.map { it.sumBy { i -> if (winning_memo[i]) 1 else 0 } }
                return if (wins[player] > wins[1 - player]) 1 else -1
            }

            state.apply(move)
        }

        return if (state.player == player) 1 else -1
    }

    fun backpropagate(leaf: Node, result: Int) {
        var node: Node? = leaf
        var result_copy = result

        while (node != null) {
            node.visits = node.visits + 1
            node.value = node.value + result_copy
            result_copy = -result_copy
            node = node.parent
        }
    }

    fun mcts(duration: Int) : Pair<Byte, Byte> {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start <= duration - 5) {
            val (node, state) = selection()

            val leaf = node.expand(state)
            state.apply(leaf.move)

            backpropagate(leaf, simulate(state))

            total_visits = total_visits + 1
        }

        System.err.println("Time: ${System.currentTimeMillis() - start}ms, Sims: ${total_visits.toInt()}")

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
        tree.apply(if (first && row == (-1).toByte()) Pair<Byte, Byte>(4, 4) else Pair(row, col))

        repeat(scanner.nextInt()) {
            scanner.nextInt()
            scanner.nextInt()
        }

        val duration = (if (first) 1000 else 100) - (System.currentTimeMillis() - start).toInt()
        System.err.println("Duration: ${duration}ms")
        val move = tree.mcts(duration)

        if (first && row == (-1).toByte()) {
            println("4 4")
        } else {
            tree.apply(move)
            println("${move.first} ${move.second}")
        }

        first = false
    }
}