package gui

import dto.JsonParce
import dto.TaskDTO
import kotlin.math.pow

class Escalonadores(private val json: JsonParce) {
    private var time: Int = 0
    private val listaStarvation = mutableListOf<Int>()
    private var computationTimeSum = 0
    private var turnaRoundTimeSoma = 0
    private var waitingTimeSoma = 0
    private var idMenorWaitingTime = Int.MAX_VALUE
    private var idMaiorWaitingTime = Int.MIN_VALUE
    private var cTime = 0
    private var cTimeSoma = 0

    fun escalonador() {
        when (json.scheduler_name) {
            "fcfs" -> fcfs()
            "rr" -> rr()
            "rm" -> rm()
            else -> edf()
        }
    }

    private fun fcfs() {
        println("Escalonador First Come First Served\n")
        reset()
        var listaChegada = json.tasks.sortedBy { it.offset }
        cTimeSoma = json.tasks.sumOf { it.computation_time }
        listaChegada = periodicoFCFS(listaChegada)
        mostrarLista(listaChegada)
        val numExecucoes = mutableMapOf<Int, Int>().withDefault { 0 }
        val waitingTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val turnaroundTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }

        while (time <= json.simulation_time) {
            var temTarefa = true
            for (tasks in listaChegada) {
                cTime = tasks.computation_time
                if (time >= json.simulation_time) break
                val tempoTask = minOf(cTime, json.simulation_time - time)
                if (time == cTimeSoma && time < tasks.period_time) time = tasks.period_time
                val waitingTime = time - tasks.offset
                val turnaroundTime = (waitingTime + cTime)
                waitingTimeSum[tasks.id_task] = waitingTimeSum.getValue(tasks.id_task) + waitingTime
                turnaroundTimeSum[tasks.id_task] = turnaroundTimeSum.getValue(tasks.id_task) + turnaroundTime
                numExecucoes[tasks.id_task] = numExecucoes.getValue(tasks.id_task) + 1
                calcWaitingTurnFCFS(tasks, tempoTask)

                if (tempoTask < cTime) temTarefa = false
            }
            if (temTarefa) break
        }
        val avgWaitingTime = waitingTimeSum.mapValues { it.value / numExecucoes.getValue(it.key) }
        val avgTurnaroundTime = turnaroundTimeSum.mapValues { it.value / numExecucoes.getValue(it.key) }

        println()
        println("Tempos de Espera por Execução:")
        avgWaitingTime.forEach { (taskId, avgWT) ->
            println("Tarefa $taskId: $avgWT")
            waitingTimeSoma += avgWT
            if (avgWT < idMenorWaitingTime) idMenorWaitingTime = taskId
            if (avgWT > idMaiorWaitingTime) idMaiorWaitingTime = taskId
        }

        println()
        println("Tempos de Turnaround por Execução:")
        avgTurnaroundTime.forEach { (taskId, avgTT) ->
            println("Tarefa $taskId: $avgTT")
            turnaRoundTimeSoma += avgTT
        }
        println()

        if (!starvationFCFS(listaChegada)) println("Não há starvation")
        mostrarWTfcfs()
    }

    private fun periodicoFCFS(listaOrganizada: List<TaskDTO>): MutableList<TaskDTO> {
        val listaChegada = mutableListOf<TaskDTO>()
        for (task in listaOrganizada) {
            var currentTime = task.offset
            while (currentTime < json.simulation_time) {
                if (currentTime + task.computation_time <= json.simulation_time) {
                    val scheduledTask = task.copy()
                    scheduledTask.offset = currentTime
                    listaChegada.add(scheduledTask)
                }
                currentTime += task.period_time
            }
        }
        listaChegada.sortBy { it.offset }
        return listaChegada
    }

    private fun calcWaitingTurnFCFS(task: TaskDTO, executionTime: Int) {
        println("Tarefa ${task.id_task} começou a executar no instante $time")
        listaStarvation.add(task.id_task)
        time += executionTime
        cTime -= executionTime
        println("Tarefa ${task.id_task} terminou sua execução no instante $time")
        computationTimeSum += executionTime
    }

    private fun starvationFCFS(listaChegada: List<TaskDTO>): Boolean {
        var ret = false
        if (
            listaChegada.any { it.id_task !in listaStarvation }) {
            for (tt in listaChegada) {
                if (tt.id_task !in listaStarvation) {
                    println("Tarefa ${tt.id_task} sofreu starvation. Não entrou para executar até o tempo limite.")
                    ret = true
                }
            }
        }
        return ret
    }

    private fun mostrarWTfcfs() {
        val waitingTimeMedio = waitingTimeSoma.toDouble() / json.tasks_number.toDouble()
        val turnaroundTimeMedio = turnaRoundTimeSoma.toDouble() / json.tasks_number.toDouble()
        println("Nível de utilização do sistema: ${computationTimeSum.toDouble() / json.simulation_time.toDouble()}")
        println("Turnaround Time médio de cada tarefa: $turnaroundTimeMedio")
        println("Waiting Time médio de cada tarefa: $waitingTimeMedio")
        println("Tarefa com menor waiting time é a de id: $idMenorWaitingTime")
        println("Tarefa com maior waiting time é a de id: $idMaiorWaitingTime")
        println()
        println("Não há inversão de prioridade")
    }

    private fun rr() {
        println("Escalonador Round Robin\n")
        reset()
        cTimeSoma = json.tasks.sumOf { it.computation_time }
        val taskMap = mutableMapOf<Int, TaskDTO>()
        val waitingTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val turnaroundTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val numExecucoes = mutableMapOf<Int, Int>().withDefault { 0 }
        val taskCompletionTimes = mutableMapOf<Int, Int>()
        val taskStartTime = mutableMapOf<Int, Int>()

        val scheduledTasks = periodicoRR(
            json.tasks.sortedWith(compareBy({ it.deadline }, { it.offset })),
            taskMap,
            waitingTimeSum,
            turnaroundTimeSum,
            numExecucoes,
            taskStartTime,
            taskCompletionTimes,
            2
        )

        val waitingTimeMedio = waitingTimeSum.values.sum().toDouble() / waitingTimeSum.keys.sumOf { taskId ->
            numExecucoes.getValue(taskId)
        }
        val turnaroundTimeMedio = turnaroundTimeSum.values.sum()
            .toDouble() / turnaroundTimeSum.keys.sumOf { taskId -> numExecucoes.getValue(taskId) }

        println()
        println("Tempos de Espera por Execução:")
        waitingTimeSum.forEach { (taskId, avgWT) ->
            println("Tarefa $taskId: $avgWT")
        }

        println()
        println("Tempos de Turnaround por Execução:")
        turnaroundTimeSum.forEach { (taskId, avgTT) ->
            println("Tarefa $taskId: $avgTT")
        }

        println()
        println("Tempo médio de espera: $waitingTimeMedio")
        println("Tempo médio de turnaround: $turnaroundTimeMedio")

        val taskStarvation = waitingTimeSum.filter { it.value > json.simulation_time * 0.75 }.keys
        println("Tarefas que sofreram starvation: $taskStarvation")
        inversaoPrioridadeRR(scheduledTasks)
    }

    private fun periodicoRR(
        taskList: List<TaskDTO>,
        taskMap: MutableMap<Int, TaskDTO>,
        waitingTimeSum: MutableMap<Int, Int>,
        turnaroundTimeSum: MutableMap<Int, Int>,
        executionCount: MutableMap<Int, Int>,
        taskStartTime: MutableMap<Int, Int>,
        taskCompletionTimes: MutableMap<Int, Int>,
        quantum: Int
    ): List<TaskDTO> {
        val timeline = MutableList(json.simulation_time) { -1 }
        val queue = mutableListOf<TaskDTO>()
        var currentTime = 0
        val tarefasCompletadas = mutableSetOf<Int>()

        while (currentTime < json.simulation_time) {
            val tasksToAdd = taskList.filter {
                it.offset in (currentTime..<currentTime + quantum)
            }
            for (task in tasksToAdd) {
                if (task.id_task !in queue.map { it.id_task }) {
                    val taskCopy = task.copy(offset = currentTime)
                    queue.add(taskCopy)
                    taskMap[task.id_task] = taskCopy
                    taskStartTime[task.id_task] = currentTime
                    taskCompletionTimes[task.id_task] = 0
                }
            }
            if (queue.isNotEmpty()) {
                val task = queue.removeAt(0)
                val timeTask = minOf(task.computation_time, quantum)
                for (t in currentTime..<currentTime + timeTask) {
                    if (t < json.simulation_time) {
                        timeline[t] = task.id_task
                    }
                }
                val updatedTask = task.copy(computation_time = task.computation_time - timeTask)
                taskMap[task.id_task] = updatedTask
                taskCompletionTimes[task.id_task] = (taskCompletionTimes[task.id_task] ?: 0) + timeTask
                if (updatedTask.computation_time > 0) {
                    queue.add(updatedTask)
                } else {
                    tarefasCompletadas.add(task.id_task)
                    val startTime = taskStartTime[task.id_task] ?: currentTime
                    val waitingTime = maxOf(0, (currentTime - startTime - task.computation_time))
                    val turnaroundTime = (currentTime - startTime)
                    waitingTimeSum[task.id_task] = waitingTimeSum.getValue(task.id_task) + waitingTime
                    turnaroundTimeSum[task.id_task] = turnaroundTimeSum.getValue(task.id_task) + turnaroundTime
                    executionCount[task.id_task] = executionCount.getValue(task.id_task) + 1
                }
                currentTime += timeTask
            } else {
                currentTime++
            }
        }
        val scheduledTasks = mutableListOf<TaskDTO>()
        timeline.forEachIndexed { time, taskId ->
            if (taskId != -1) {
                val task = taskMap[taskId]
                task?.let {
                    scheduledTasks.add(it.copy(offset = time))
                }
            }
        }

        val usedTime = timeline.count { it != -1 }
        val systemUtilization = usedTime.toDouble() / json.simulation_time * 100
        println("Utilização do sistema: $systemUtilization%")

        println("Tarefas escalonadas:")
        for (t in scheduledTasks) println("Tarefa ${t.id_task} no tempo ${t.offset}")
        verificarPerdaDeadlineRR(scheduledTasks)
        return scheduledTasks
    }

    private fun verificarPerdaDeadlineRR(tarefas: List<TaskDTO>) {
        val deadlinesPerdidos = mutableSetOf<Int>()
        for (task in tarefas) {
            val periodo = task.period_time
            var instantes = task.offset

            while (instantes <= json.simulation_time) {
                if (instantes + task.computation_time > instantes + task.deadline) {
                    deadlinesPerdidos.add(task.id_task)
                    break
                }
                instantes += periodo
            }
        }
        for (idTask in deadlinesPerdidos) {
            println("Tarefa $idTask perdeu o seu deadline.")
        }
    }

    private fun inversaoPrioridadeRR(scheduledTasks: List<TaskDTO>) {
        val taskPriorities = mutableMapOf<Int, Int>()
        val taskExecutionTimes = mutableMapOf<Int, MutableList<Int>>()

        scheduledTasks.forEach { task ->
            val priority = task.priority
            val executionTimes = taskExecutionTimes.getOrPut(task.id_task) { mutableListOf() }
            executionTimes.add(task.offset)
            taskPriorities[task.id_task] = priority
        }
        for ((taskId, priority) in taskPriorities) {
            val executionTimes = taskExecutionTimes[taskId] ?: continue
            val higherPriorityTasks = taskPriorities.filter { it.value < priority }.keys
            if (higherPriorityTasks.isNotEmpty()) {
                val inversionDetected = executionTimes.any { time ->
                    higherPriorityTasks.any { higherTaskId ->
                        taskExecutionTimes[higherTaskId]?.any { higherTime -> higherTime < time } ?: false
                    }
                }
                if (inversionDetected) {
                    println("Inversão de prioridade detectada para a tarefa $taskId com prioridade $priority")
                } else {
                    println("Não houve inversão de prioridade")
                }
            }
        }
    }

    private fun rm() {
        println("Escalonador Rate Monotonic\n")
        reset()

        val waitingTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val turnaroundTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val executionCount = mutableMapOf<Int, Int>().withDefault { 0 }

        val scheduledTasks = periodicoRM(json.tasks, waitingTimeSum, turnaroundTimeSum, executionCount)

        val totalUtilization = json.tasks.sumOf { it.computation_time.toDouble() / it.period_time }
        println("Utilização total do sistema: $totalUtilization")

        val totalExecutions = executionCount.values.sum()
        val overallAvgWaitingTime =
            if (totalExecutions > 0) waitingTimeSum.values.sum().toDouble() / totalExecutions else Double.NaN
        val overallAvgTurnaroundTime =
            if (totalExecutions > 0) turnaroundTimeSum.values.sum().toDouble() / totalExecutions else Double.NaN

        println()
        println("Tempos de Espera por Execução:")
        waitingTimeSum.forEach { (taskId, totalWT) ->
            val avgWT =
                if (executionCount.getValue(taskId) > 0) totalWT.toDouble() / executionCount.getValue(taskId) else Double.NaN
            println("Tarefa $taskId: $avgWT")
        }

        println()
        println("Tempos de Turnaround por Execução:")
        turnaroundTimeSum.forEach { (taskId, totalTT) ->
            val avgTT =
                if (executionCount.getValue(taskId) > 0) totalTT.toDouble() / executionCount.getValue(taskId) else Double.NaN
            println("Tarefa $taskId: $avgTT")
        }

        println()
        println("Tempo médio de espera: $overallAvgWaitingTime")
        println("Tempo médio de turnaround: $overallAvgTurnaroundTime")

        val maxWaitingTimeTask = waitingTimeSum.maxByOrNull { it.value / (executionCount[it.key] ?: 1) }
        val minWaitingTimeTask = waitingTimeSum.minByOrNull { it.value / (executionCount[it.key] ?: 1) }
        println(
            "Tarefa com maior waiting time médio: ${maxWaitingTimeTask?.key}, Tempo: ${
                maxWaitingTimeTask?.value?.toDouble()?.div(executionCount[maxWaitingTimeTask.key] ?: 1)
            }"
        )
        println(
            "Tarefa com menor waiting time médio: ${minWaitingTimeTask?.key}, Tempo: ${
                minWaitingTimeTask?.value?.toDouble()?.div(executionCount[minWaitingTimeTask.key] ?: 1)
            }"
        )

        verificarPerdaDeadlineRM(scheduledTasks, mutableListOf(), mutableMapOf())
        println()
        testeEscalonabilidadeRM()
        //calcEscalonabilidadeEDFRM(scheduledTasks)
        println()
        println("Não há inversão de prioridade")
    }

    private fun periodicoRM(
        taskList: List<TaskDTO>,
        waitingTimeSum: MutableMap<Int, Int>,
        turnaroundTimeSum: MutableMap<Int, Int>,
        executionCount: MutableMap<Int, Int>
    ): List<TaskDTO> {
        val timeline = MutableList(json.simulation_time) { -1 }
        val activeTasks = mutableListOf<TaskDTO>()
        val taskStartTime = mutableMapOf<Int, Int>()

        val sortedTaskList = taskList.sortedBy { it.period_time }

        var currentTime = 0
        while (currentTime < json.simulation_time) {
            for (task in sortedTaskList) {
                if ((currentTime - task.offset) % task.period_time == 0 && currentTime >= task.offset) {
                    val taskCopy = task.copy(offset = currentTime, computation_time = task.computation_time)
                    activeTasks.add(taskCopy)
                    taskStartTime[task.id_task] = currentTime
                }
            }

            activeTasks.sortBy { it.period_time }

            if (activeTasks.isNotEmpty()) {
                val task = activeTasks.first()
                if (task.computation_time > 0) {
                    timeline[currentTime] = task.id_task
                    val updatedTask = task.copy(computation_time = task.computation_time - 1)
                    activeTasks[0] = updatedTask
                    if (updatedTask.computation_time == 0) {
                        activeTasks.removeAt(0)
                        val startTime = taskStartTime[task.id_task] ?: currentTime
                        val waitingTime = maxOf(0, (currentTime - startTime - task.computation_time))
                        val turnaroundTime = (currentTime - startTime)
                        waitingTimeSum[task.id_task] = waitingTimeSum.getValue(task.id_task) + waitingTime
                        turnaroundTimeSum[task.id_task] = turnaroundTimeSum.getValue(task.id_task) + turnaroundTime
                        executionCount[task.id_task] = executionCount.getValue(task.id_task) + 1
                    }
                }
            }
            currentTime++
        }

        val scheduledTasks = mutableListOf<TaskDTO>()
        timeline.forEachIndexed { time, taskId ->
            if (taskId != -1) {
                val task = taskList.find { it.id_task == taskId }
                task?.let {
                    scheduledTasks.add(it.copy(offset = time))
                }
            }
        }

        println("Tarefas escalonadas:")
        for (t in scheduledTasks) println("Tarefa ${t.id_task} no tempo ${t.offset}")


        return scheduledTasks
    }

    private fun verificarPerdaDeadlineRM(
        scheduledTasks: List<TaskDTO>,
        timeline: MutableList<Int>,
        taskMap: MutableMap<Int, TaskDTO>
    ) {
        val deadlines = mutableMapOf<Int, Int>()
        val missedDeadlines = mutableMapOf<Int, Int>()

        scheduledTasks.forEach { task ->
            val periodDeadline = task.offset + task.period_time
            deadlines[task.id_task] = periodDeadline
        }

        timeline.forEachIndexed { time, taskId ->
            if (taskId != -1) {
                val task = taskMap[taskId]
                task?.let {
                    val deadline = deadlines[task.id_task] ?: Int.MAX_VALUE
                    if (time > deadline) {
                        missedDeadlines[task.id_task] = missedDeadlines.getOrDefault(task.id_task, 0) + 1
                    }
                }
            }
        }

        if (missedDeadlines.isNotEmpty()) {
            println("Tarefas com perda de deadline:")
            missedDeadlines.forEach { (taskId, count) ->
                println("Tarefa $taskId: $count perda(s) de deadline")
            }
        } else {
            println("Nenhuma tarefa perdeu deadline.")
        }
    }

    private fun testeEscalonabilidadeRM() {
        val numTasks = json.tasks.size
        val totalUtilization = json.tasks.sumOf { it.computation_time.toDouble() / it.period_time }
        val limit = numTasks * (2.0.pow(1.0 / numTasks) - 1)

        println("Utilização total do sistema: $totalUtilization")
        println("Limite de escalonabilidade para $numTasks tarefas: $limit")

        if (totalUtilization <= limit) {
            println("O sistema é escalonável com Rate Monotonic.")
        } else {
            println("O sistema não é escalonável com Rate Monotonic.")
        }
    }

    private fun calcEscalonabilidadeEDFRM(utilizacaoTarefas: List<Double>, listaChegada: List<TaskDTO>) {
        val utilizacaoTotal = utilizacaoTarefas.sum()
        val numeroTarefas = listaChegada.size
        fun mmc(periodos: List<Int>): Int {
            fun gcd(a: Int, b: Int): Int {
                return if (b == 0) a else gcd(b, a % b)
            }
            return periodos.reduce { acc, periodo -> acc * (periodo / gcd(acc, periodo)) }
        }

        val periodos = listaChegada.map { it.period_time }
        val mmcPeriodos = mmc(periodos)
        val periodosHarmonicos = periodos.all { mmcPeriodos % it == 0 }
        val limiteEscalonabilidade = numeroTarefas * (2.0.pow(1.0 / numeroTarefas) - 1)
        val escalonavel = utilizacaoTotal <= limiteEscalonabilidade
        val statusEscalonamento = when {
            periodosHarmonicos -> {
                "Os períodos das tarefas são harmônicos e o teste de escalonabilidade é 100%."
            }

            escalonavel -> {
                "Escalonável: $utilizacaoTotal <= $limiteEscalonabilidade"
            }

            else -> {
                "Não escalonável: $utilizacaoTotal > $limiteEscalonabilidade"
            }
        }

        println()
        println("Teste de escalonabilidade:")
        println("Limite de escalonabilidade: $limiteEscalonabilidade")
        println("O conjunto de tarefas é escalonável? $statusEscalonamento\n")
    }

    private fun edf() {
        println("Escalonador Earliest Deadline First\n")
        reset()

        val taskMap = mutableMapOf<Int, TaskDTO>()
        val waitingTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val turnaroundTimeSum = mutableMapOf<Int, Int>().withDefault { 0 }
        val executionCount = mutableMapOf<Int, Int>().withDefault { 0 }
        val deadlineMissed = mutableSetOf<Int>()
        val taskActivations = mutableMapOf<Int, Int>().withDefault { 0 }

        val taskPreemptionCount = mutableMapOf<Int, Int>().withDefault { 0 }
        val taskWaitTime = mutableMapOf<Int, Int>().withDefault { 0 }

        val listaChegada = periodicoEDF(
            json.tasks.sortedWith(compareBy({ it.deadline }, { it.offset })),
            taskMap,
            waitingTimeSum,
            turnaroundTimeSum,
            executionCount,
            deadlineMissed,
            taskActivations,
            taskPreemptionCount,
            taskWaitTime
        )

        verificarInversaoPrioridadeEDF(json.tasks, listaChegada.map { it.id_task })
        println()
        if (!verificarStarvationEDF(taskPreemptionCount, taskWaitTime)) println("Não há starvation")

        println()
        if (deadlineMissed.isNotEmpty()) {
            println("Tarefas que sofreram perda de deadline:")
            deadlineMissed.forEach { println("Tarefa $it") }
        } else {
            println("Nenhuma tarefa sofreu perda de deadline.")
        }

        val overallAvgWaitingTime = waitingTimeSum.values.sum().toDouble() / executionCount.values.sum()
        val overallAvgTurnaroundTime = turnaroundTimeSum.values.sum().toDouble() / executionCount.values.sum()

        println()
        println("Tempos de Espera por Execução:")
        waitingTimeSum.forEach { (taskId, avgWT) ->
            println("Tarefa $taskId: $avgWT")
        }

        println()
        println("Tempos de Turnaround por Execução:")
        turnaroundTimeSum.forEach { (taskId, avgTT) ->
            println("Tarefa $taskId: $avgTT")
        }

        println()
        println("Tempo médio de espera: $overallAvgWaitingTime")
        println("Tempo médio de turnaround: $overallAvgTurnaroundTime")

        val totalExecutionTime = turnaroundTimeSum.values.sum().toDouble()
        val utilization = totalExecutionTime / json.simulation_time
        println("Nível de utilização do sistema: $utilization")

        val maxWaitingTimeTask = waitingTimeSum.maxByOrNull { it.value }
        val minWaitingTimeTask = waitingTimeSum.minByOrNull { it.value }
        println("Tarefa com maior waiting time médio: ${maxWaitingTimeTask?.key}, valor: ${maxWaitingTimeTask?.value}")
        println("Tarefa com menor waiting time médio: ${minWaitingTimeTask?.key}, valor: ${minWaitingTimeTask?.value}")

        val totalActivations = taskActivations.values.sum()
        val deadlineMisses = deadlineMissed.size
        val frequency = if (totalActivations > 0) deadlineMisses.toDouble() / totalActivations else 0.0
        println("Frequência de perda de deadline: $frequency")

        println()
        taskWaitTime.forEach { (taskId, waitTime) ->
            val preemptions = taskPreemptionCount.getValue(taskId)
            if (preemptions > 0) {
                println("Tarefa $taskId sofreu starvation: Tempo total de espera: $waitTime, Número de preempções: $preemptions")
            }
        }

        val utilizacaoTarefas = json.tasks.map { it.computation_time.toDouble() / it.period_time }
        calcEscalonabilidadeEDF(utilizacaoTarefas, listaChegada)
    }

    private fun periodicoEDF(
        taskList: List<TaskDTO>,
        taskMap: MutableMap<Int, TaskDTO>,
        waitingTimeSum: MutableMap<Int, Int>,
        turnaroundTimeSum: MutableMap<Int, Int>,
        executionCount: MutableMap<Int, Int>,
        deadlineMissed: MutableSet<Int>,
        taskActivations: MutableMap<Int, Int>,
        taskPreemptionCount: MutableMap<Int, Int>,
        taskWaitTime: MutableMap<Int, Int>
    ): List<TaskDTO> {
        val timeline = MutableList(json.simulation_time) { -1 }
        val activeTasks = mutableListOf<Pair<Int, TaskDTO>>()
        var currentTime = 0
        val tarefasCompletadas = mutableSetOf<Int>()
        val taskStartTime = mutableMapOf<Int, Int>()

        while (currentTime < json.simulation_time) {
            for (task in taskList) {
                if ((currentTime - task.offset) % task.period_time == 0 && currentTime >= task.offset) {
                    val taskCopy = task.copy(offset = currentTime)
                    activeTasks.add(Pair(currentTime + task.deadline, taskCopy))
                    taskMap[task.id_task] = taskCopy
                    taskStartTime[task.id_task] = currentTime
                    taskActivations[task.id_task] = taskActivations.getValue(task.id_task) + 1
                }
            }
            activeTasks.map { it.second.id_task }.toSet()
            activeTasks.removeAll { it.first <= currentTime }
            activeTasks.sortBy { it.first }
            if (activeTasks.isNotEmpty()) {
                val (taskDeadline, task) = activeTasks.first()
                if (task.computation_time > 0) {
                    timeline[currentTime] = task.id_task
                    val updatedTask = task.copy(computation_time = task.computation_time - 1)
                    activeTasks[0] = Pair(taskDeadline, updatedTask)
                    taskMap[task.id_task] = updatedTask
                    if (updatedTask.computation_time == 0) {
                        activeTasks.removeAt(0)
                        tarefasCompletadas.add(task.id_task)
                        val startTime = taskStartTime[task.id_task] ?: currentTime
                        val waitingTime = maxOf(0, (currentTime - startTime - task.computation_time))
                        val turnaroundTime = (currentTime - startTime)
                        waitingTimeSum[task.id_task] = waitingTimeSum.getValue(task.id_task) + waitingTime
                        turnaroundTimeSum[task.id_task] = turnaroundTimeSum.getValue(task.id_task) + turnaroundTime
                        executionCount[task.id_task] = executionCount.getValue(task.id_task) + 1
                    }
                }
            } else {
                val allTaskIds = taskList.map { it.id_task }.toSet()
                val activeTaskIds = activeTasks.map { it.second.id_task }.toSet()
                val preemptedTaskIds = allTaskIds - activeTaskIds
                preemptedTaskIds.forEach { taskId ->
                    taskPreemptionCount[taskId] = taskPreemptionCount.getValue(taskId) + 1
                    taskWaitTime[taskId] = taskWaitTime.getValue(taskId) + 1
                }
                if (allTaskIds != activeTaskIds) {
                    deadlineMissed.addAll(allTaskIds - activeTaskIds)
                }
            }
            currentTime++
        }

        val scheduledTasks = mutableListOf<TaskDTO>()
        timeline.forEachIndexed { time, taskId ->
            if (taskId != -1) {
                val task = taskMap[taskId]
                task?.let {
                    scheduledTasks.add(it.copy(offset = time))
                }
            }
        }

        println("Tarefas escalonadas:")
        for (t in scheduledTasks) println("Tarefa ${t.id_task} no tempo ${t.offset}")

        verificarPerdaDeadlineEDF(scheduledTasks)

        return scheduledTasks
    }

    private fun verificarPerdaDeadlineEDF(tarefas: List<TaskDTO>) {
        val deadlinesPerdidos = mutableSetOf<Int>()
        for (task in tarefas) {
            val periodo = task.period_time
            var instantes = task.offset

            while (instantes <= json.simulation_time) {
                if (instantes + task.computation_time > instantes + task.deadline) {
                    deadlinesPerdidos.add(task.id_task)
                    break
                }
                instantes += periodo
            }
        }
        for (idTask in deadlinesPerdidos) {
            println("Tarefa $idTask perdeu o seu deadline.")
        }
    }

    private fun calcEscalonabilidadeEDF(utilizacaoTarefas: List<Double>, listaChegada: List<TaskDTO>) {
        val utilizacaoTotal = utilizacaoTarefas.sum()
        val numeroTarefas = listaChegada.size
        fun mmc(periodos: List<Int>): Int {
            fun gcd(a: Int, b: Int): Int {
                return if (b == 0) a else gcd(b, a % b)
            }
            return periodos.reduce { acc, periodo -> acc * (periodo / gcd(acc, periodo)) }
        }

        val periodos = listaChegada.map { it.period_time }
        val mmcPeriodos = mmc(periodos)
        val periodosHarmonicos = periodos.all { mmcPeriodos % it == 0 }
        val limiteEscalonabilidade = numeroTarefas * (2.0.pow(1.0 / numeroTarefas) - 1)
        val escalonavel = utilizacaoTotal <= limiteEscalonabilidade
        val statusEscalonamento = when {
            periodosHarmonicos -> {
                "Os períodos das tarefas são harmônicos e o teste de escalonabilidade é 100%."
            }

            escalonavel -> {
                "Escalonável: $utilizacaoTotal <= $limiteEscalonabilidade"
            }

            else -> {
                "Não escalonável: $utilizacaoTotal > $limiteEscalonabilidade"
            }
        }

        println()
        println("Teste de escalonabilidade:")
        println("Limite de escalonabilidade: $limiteEscalonabilidade")
        println("O conjunto de tarefas é escalonável? $statusEscalonamento\n")
    }

    private fun verificarInversaoPrioridadeEDF(
        taskList: List<TaskDTO>,
        timeline: List<Int>
    ) {
        val prioridadeTarefas = taskList.associateBy { it.id_task }
        val inversoes = mutableListOf<Pair<Int, Int>>()

        for (time in timeline.indices) {
            val taskId = timeline[time]
            if (taskId != -1) {
                val tarefaAtual = prioridadeTarefas[taskId]
                tarefaAtual?.let { it ->
                    val tasksAtTime = timeline.slice(time..<time + it.computation_time).filter { it != -1 }
                    if (tasksAtTime.isNotEmpty() && tasksAtTime.first() != taskId) {
                        tasksAtTime.forEach { id ->
                            if (id != taskId) {
                                val tarefaPreemptora = prioridadeTarefas[id] ?: return@forEach
                                if (tarefaPreemptora.deadline < it.deadline) {
                                    inversoes.add(Pair(tarefaPreemptora.id_task, it.id_task))
                                }
                            }
                        }
                    }
                }
            }
        }

        println()
        if (inversoes.isNotEmpty()) {
            println("Inversões de prioridade detectadas:")
            inversoes.forEach { (preemptora, preemptada) ->
                println("Tarefa $preemptora preemptou a tarefa $preemptada")
            }
        } else {
            println("Nenhuma inversão de prioridade detectada.")
        }
    }

    private fun verificarStarvationEDF(
        taskPreemptionCount: Map<Int, Int>,
        taskWaitTime: Map<Int, Int>,
        threshold: Int = 0
    ): Boolean {
        var ret = false
        taskPreemptionCount.forEach { (taskId, preemptions) ->
            val waitTime = taskWaitTime.getValue(taskId)
            if (preemptions > threshold && waitTime > threshold) {
                println("Tarefa $taskId sofreu starvation: Tempo total de espera: $waitTime, Número de preempções: $preemptions")
                ret = true
            }
        }
        return ret
    }

    private fun mostrarLista(listaChegada: List<TaskDTO>) {
        println()
        println("Lista ordenada a ser executada:")
        for (lista in listaChegada)
            println("Tarefa " + lista.id_task)
        println()
    }

    private fun reset() {
        computationTimeSum = 0
        turnaRoundTimeSoma = 0
        waitingTimeSoma = 0
        idMenorWaitingTime = Int.MAX_VALUE
        idMaiorWaitingTime = Int.MIN_VALUE
    }

}
