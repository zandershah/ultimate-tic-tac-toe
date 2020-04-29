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

data class State(
    val board: Array<Array<Int>> = Array(2, { Array(9, { 0 }) }),
    var last_move: Pair<Int, Int> = Pair(0, 0),
    var player: Int = 0
) {
    fun deep_copy(
        board: Array<Array<Int>> = arrayOf(this.board[0].copyOf(), this.board[1].copyOf()),
        last_move: Pair<Int, Int> = this.last_move,
        player: Int = this.player
    ) = State(board, last_move, player)

    fun apply(move: Pair<Int, Int>) {
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

    fun moves(): List<Pair<Int, Int>> {
        val small_row = last_move.first % 3
        val small_col = last_move.second % 3
        var small_board = small_row * 3 + small_col

        var moves = (0 until 9).filter {
            ((board[0][small_board] or board[1][small_board]) and (1 shl it)) == 0
        }.map { Pair (it / 3 + 3 * small_row, it % 3 + 3 * small_col)}

        // Move anywhere valid.
        if (moves.isEmpty() || winning_memo[board[0][small_board]] || winning_memo[board[1][small_board]]) {
            moves = (0 until 81).filter {
                small_board = it / 9 / 3 * 3 + it % 9 / 3
                val bitwise_move = 1 shl (it / 9 % 3 * 3 + it % 3)
                val empty = ((board[0][small_board] or board[1][small_board]) and bitwise_move) == 0
                val unfinished = !winning_memo[board[0][small_board]] && !winning_memo[board[1][small_board]]
                empty && unfinished
            }.map { Pair (it / 9, it % 9) }
        }

        // TODO: Move anywhere on the first turn.
        return moves
    }

    fun debug() {
        for (i in 0 until 9) {
            if (i != 0 && i % 3 == 0) {
                System.err.println("-----------")
            }
            var row = ""
            for (j in 0 until 9) {
                if (j != 0 && j % 3 == 0) {
                    row = row + "|"
                }
                val small_board = i % 3 * 3 + j % 3
                val move = 1 shl small_board
                if ((board[0][small_board] and move) != 0) {
                    row = row + "X"
                } else if ((board[1][small_board] and move) != 0) {
                    row = row + "O"
                } else {
                    row = row + "."
                }
            }
            System.err.println(row)
        }
    }
}

class Tree {
    // TODO: Equality might be broken?
    inner class Node(
        val parent: Node? = null,
        val move: Pair<Int, Int> = Pair(0, 0)
    ) {
        val children: MutableList<Node> = mutableListOf()
        var visits = 1E-9
        var value = 1E-6

        fun leaf() = children.isEmpty()

        fun expand(state: State) : Node {
            if (state.winning()) return this

            val moves = state.moves()
            moves.forEach{ children.add(Node(this, it)) }

            return if (children.isEmpty()) this else children.last()
        }
    }

    var root = Node()
    var root_state = State()
    var total_visits = 0.0

    // TODO: Recycle subtree if possible.
    fun apply(move: Pair<Int, Int>) {
        if (move.first == -1) return

        root = Node(move=move)
        root_state.apply(move)
        total_visits = 0.0
    }

    // TODO: Not efficient?
    fun selection(): Pair<Node, State> {
        var node = root
        val state = root_state.deep_copy()

        // TODO: Set exploration parameter.
        val c = sqrt(2.0)
        while (!node.leaf()) {
            // TODO: UCB might be wrong.
            node = node.children.maxBy { it.value / it.visits + c * sqrt(ln(total_visits) / it.visits) }!!
            state.apply(node.move)
        }

        return Pair(node, state)
    }

    // TODO: Heuristic simulation.
    fun simulate(state: State) : Double {
        while (!state.winning()) {
            val moves = state.moves()
            if (moves.isEmpty()) return 0.0

            state.apply(moves.random())
        }

        return if (state.player == root_state.player) 1.0 else -1.0
    }

    fun backpropagate(leaf: Node?, result: Double) {
        if (leaf == null) { return }

        val node = leaf
        node.visits = node.visits + 1
        node.value = node.value + result

        backpropagate(node.parent, -result)
    }

    fun mcts() : Pair<Int, Int> {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start <= 95) {
            val (node, state) = selection()

            val leaf = node.expand(state)
            state.apply(leaf.move)

            val result = simulate(state)

            backpropagate(leaf, result)
            total_visits = total_visits + 1
        }
        System.err.println("${System.currentTimeMillis() - start}, ${total_visits}")

        assert(!root.children.isEmpty())
        return root.children.maxBy { it.visits }!!.move
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    var tree = Tree()

    while (true) {
        val row = scanner.nextInt()
        val col = scanner.nextInt()
        tree.apply(Pair(row, col))

        repeat(scanner.nextInt()) {
            scanner.nextInt()
            scanner.nextInt()
        }

        val move = tree.mcts()
        tree.apply(move)

        println("${move.first} ${move.second}")
    }
}