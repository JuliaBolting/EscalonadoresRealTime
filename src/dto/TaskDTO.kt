package dto

data class TaskDTO(
    var id_task: Int = 0,
    var offset: Int = 0,
    var computation_time: Int = 0,
    var period_time: Int = 0,
    var quantum: Int = 0,
    var deadline: Int = 0,
    var priority: Int = 0
)
