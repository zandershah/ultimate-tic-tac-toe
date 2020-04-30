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
                val small_board = i / 3 * 3 + j / 3
                val move = 1 shl (i % 3 * 3 + j % 3)
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

    inner class Timing(
        var current_time: Double = 0.0,
        var selection: Double = 0.0,
        var expansion: Double = 0.0,
        var simulation: Double = 0.0,
        var backpropagation: Double = 0.0
    ) {

        fun reset() {
            current_time = System.nanoTime().toDouble()
            selection = 0.0
            expansion = 0.0
            simulation = 0.0
            backpropagation = 0.0
        }

        fun lap(): Double {
            val delta = System.nanoTime() - current_time
            current_time = System.nanoTime().toDouble()
            return delta
        }
    }

    var root = Node()
    var root_state = State()
    var total_visits = 0.0
    var timing = Timing()

    fun apply(move: Pair<Int, Int>) {
        if (move.first == -1) return

        // TODO: This is making me timeout.
        // root = root.children.find { it.move == move } ?: Node(move=move)
        root = Node(move=move)
        total_visits = root.visits

        root_state.apply(move)
    }

    // TODO: Not efficient?
    fun selection(): Pair<Node, State> {
        var node = root
        val state = root_state.deep_copy()

        // TODO: Set exploration parameter.
        val c = sqrt(2.0 * ln(total_visits))
        while (!node.leaf()) {
            node = node.children.maxBy { it.value / it.visits + c / sqrt(it.visits) }!!
            state.apply(node.move)
        }

        return Pair(node, state)
    }

    // TODO: Heuristic simulation.
    fun simulate(state: State) : Double {
        val player = state.player

        while (!state.winning()) {
            val moves = state.moves()
            // TODO: Count small boards for tie-breaker.
            if (moves.isEmpty()) {
                // Tie-breaker.
                val wins = state.board.map { it.sumBy { i -> if (winning_memo[i]) 1 else 0 } }
                return if (wins[player] > wins[1 - player]) 1.0 else -1.0
            }

            state.apply(moves.random())
        }

        return if (state.player == player) 1.0 else -1.0
    }

    fun backpropagate(leaf: Node, result: Double) {
        var node: Node? = leaf
        var flipping_result = result

        while (node != null) {
            node.visits = node.visits + 1
            node.value = node.value + flipping_result
            flipping_result = -flipping_result
            node = node.parent
        }
    }

    fun mcts(duration: Int) : Pair<Int, Int> {
        val start = System.currentTimeMillis()
        timing.reset()
        while (System.currentTimeMillis() - start <= duration - 1.5) {
            timing.lap()
            val (node, state) = selection()
            timing.selection = timing.selection + timing.lap()

            val leaf = node.expand(state)
            timing.expansion = timing.expansion + timing.lap()
            state.apply(leaf.move)

            val result = simulate(state)
            timing.simulation = timing.simulation + timing.lap()

            backpropagate(leaf, result)
            timing.backpropagation = timing.backpropagation + timing.lap()

            total_visits = total_visits + 1
        }

        System.err.println("Time: ${System.currentTimeMillis() - start}ms, Sims: ${total_visits.toInt()}")
        System.err.println("Selection: ${timing.selection * 1E-6}ms")
        System.err.println("Expansion: ${timing.expansion * 1E-6}ms")
        System.err.println("Simulation: ${timing.simulation * 1E-6}ms")
        System.err.println("Backpropogation: ${timing.backpropagation / 1E-6}ms")

        assert(!root.children.isEmpty())
        return root.children.maxBy { it.visits }!!.move
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    var tree = Tree()
    var first = true

    // First turn.
    while (true) {
        val row = scanner.nextInt()
        val col = scanner.nextInt()

        val start = System.currentTimeMillis()
        tree.apply(if (first && row == -1) Pair(4, 4) else Pair(row, col))

        repeat(scanner.nextInt()) {
            scanner.nextInt()
            scanner.nextInt()
        }

        val duration = (if (first) 1000 else 100) - (System.currentTimeMillis() - start).toInt()
        System.err.println("Duration: ${duration}ms")
        val move = tree.mcts(duration)

        if (first && row == -1) {
            println("4 4")
        } else {
            tree.apply(move)
            println("${move.first} ${move.second}")
        }

        first = false
    }
}