package com.anitha.remindme.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitha.remindme.data.TaskEntity
import com.anitha.remindme.databinding.ItemTaskBinding

class TaskAdapter(
    private val onToggleComplete: (TaskEntity, Boolean) -> Unit,
    private val onEdit: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, TaskViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding, onToggleComplete, onEdit)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TaskEntity>() {
            override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem == newItem
        }
    }
}

class TaskViewHolder(
    private val binding: ItemTaskBinding,
    private val onToggleComplete: (TaskEntity, Boolean) -> Unit,
    private val onEdit: (TaskEntity) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(task: TaskEntity) {
        binding.textTitle.text = task.title
        binding.textTime.text = task.time
        binding.checkCompleted.setOnCheckedChangeListener(null)
        binding.checkCompleted.isChecked = task.completed
        binding.checkCompleted.isEnabled = !task.completed
        binding.checkCompleted.setOnCheckedChangeListener { _, isChecked -> onToggleComplete(task, isChecked) }
        binding.btnEdit.setOnClickListener { onEdit(task) }
    }
}
