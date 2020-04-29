import java.util.*
import kotlin.io.*
import kotlin.math.*

class Tree {
    // TODO: Equality might be broken?
    inner class Node(
        val parent: Node? = null,
        val move: Pair<Int, Int> = Pair(-1, -1)
    ) {
        val children: MutableList<Node> = mutableListOf()
        var visits = 1E-9
        var value = 1E-6

        fun leaf() = children.isEmpty()

        fun expand(state: Array<Int>) : Node {
            if (winning_memo[state[0]] or winning_memo[state[1]]) return this

            val moves = moves(state).shuffled()
            moves.forEach{ children.add(Node(this, it)) }

            return if (children.isEmpty()) this else children.last()
        }
    }

    var root = Node()
    var total_visits = 0.0
    val root_state = Array(2) {0}
    var root_player = 0

    val winning_memo = run {
        val winning: MutableList<Int> = mutableListOf()

        // Diagonals.
        winning.add((1 shl 0) + (1 shl 4) + (1 shl 8))
        winning.add((1 shl 2) + (1 shl 4) + (1 shl 6))

        for (i in 0 .. 2) {
            winning.add(7 shl (i * 3))
            winning.add((1 shl i) + (1 shl (i + 3)) + (1 shl (i + 6)))
        }

        Array(1 shl 9, { i -> winning.any { (it and i) == it }})
    }

    fun moves(state: Array<Int>) = (0 until 9).filter {
        ((state[root_player] or state[1 - root_player]) and (1 shl it)) == 0
    }.map { Pair(it / 3, it % 3) }

    // TODO: Recycle subtree if possible.
    fun apply(move: Pair<Int, Int>) {
        if (move.first == -1) { return }

        root = Node(move=move)
        root_state[root_player] = root_state[root_player] or (1 shl (3 * move.first + move.second))
        root_player = 1 - root_player
    }

    // TODO: Not efficient?
    fun selection(): Pair<Node, Array<Int>> {
        var node = root
        val state = root_state.copyOf()
        var player = root_player

        // TODO: Set exploration parameter.
        val c = sqrt(2.0)
        while (!node.leaf()) {
            // TODO: UCB might be wrong.
            node = node.children.maxBy { it.value / it.visits + c * sqrt(ln(total_visits) / it.visits) }!!
            state[player] = state[player] or (1 shl (3 * node.move.first + node.move.second))
            player = 1 - player
        }
        return Pair(node, state)
    }

    // TODO: Heuristic simulation.
    fun simulate(state: Array<Int>) : Double {
        var player = root_player

        while (!winning_memo[state[0]] && !winning_memo[state[1]]) {
            player = 1 - player

            val moves = moves(state)
            if (moves.isEmpty()) return 0.0

            val move = moves.random()
            state[player] = state[player] or (1 shl (3 * move.first + move.second))
        }

        return if (player == root_player) 1.0 else -1.0
    }

    fun backpropagate(leaf: Node?, result: Double) {
        if (leaf == null) { return }

        val node = leaf
        node.visits = node.visits + 1
        node.value = node.value + result
        backpropagate(node.parent, -result)
    }

    // TODO: Remove.
    fun debug(node: Node = root, depth: Int = 0, state: Array<Int> = root_state, player: Int = root_player) {
        if (depth > 3) return
        System.err.println()
        System.err.println("EV: ${node.value / node.visits}")
        for (i in 0 .. 2) {
            var l = ""
            repeat(depth) { l += "\t" }
            for (j in 0 .. 2) {
                if ((state[0] and (1 shl (3 * i + j))) != 0) {
                    l += 'X'
                } else if ((state[1] and (1 shl (3 * i + j))) != 0) {
                    l += 'O'
                } else {
                    l += '.'
                }
            }
            System.err.println(l)
        }
        for (child in node.children) {
            val s = state.copyOf()
            s[player] = s[player] or (1 shl (3 * child.move.first + child.move.second))
            debug(child, depth + 1, s, 1 - player)
        }
    }

    fun mcts(time: Double) : Pair<Int, Int> {
        repeat(100) {
            var (node, state) = selection()
            val leaf = node.expand(state)

            state[root_player] = state[root_player] or (1 shl (3 * leaf.move.first + leaf.move.second))
            val result = simulate(state)
            backpropagate(leaf, result)

            total_visits = total_visits + 1
        }

        debug()
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

        val move = tree.mcts(100.0)
        tree.apply(move)

        println("${move.first} ${move.second}")
    }
}