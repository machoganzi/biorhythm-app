// app/src/main/java/com/jjangdol/biorhythm/ui/main/UserNotificationAdapter.kt
package com.jjangdol.biorhythm.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemUserNotificationBinding
import com.jjangdol.biorhythm.data.model.Notification
import com.jjangdol.biorhythm.data.model.NotificationPriority
import java.text.SimpleDateFormat
import java.util.*

class UserNotificationAdapter(
    private val onItemClick: (Notification) -> Unit,
    private val onMoreClick: (Notification, Boolean) -> Unit,
    private val onMarkReadClick: (Notification) -> Unit,
    private val onShareClick: (Notification) -> Unit,
    private val isNotificationRead: (String) -> Boolean  // 읽음 상태 확인 함수
) : ListAdapter<Notification, UserNotificationAdapter.UserNotificationViewHolder>(NotificationDiffCallback()) {

    private val selectedItems = mutableSetOf<String>()
    private val expandedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserNotificationViewHolder {
        val binding = ItemUserNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserNotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserNotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserNotificationViewHolder(
        private val binding: ItemUserNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            binding.apply {
                val isSelected = notification.id in selectedItems
                val isExpanded = notification.id in expandedItems
                val isRead = isNotificationRead(notification.id)  // 실제 읽음 상태 확인

                // 기본 정보 설정
                tvNotificationTitle.text = notification.title
                tvNotificationContent.text = notification.content
                tvFullContent.text = notification.content

                // 우선순위 설정
                tvPriority.text = notification.priority.displayName
                val priorityColor = Color.parseColor(notification.priority.colorRes)
                tvPriority.setTextColor(priorityColor)
                priorityIndicator.setBackgroundColor(priorityColor)

                // 날짜 설정
                val dateFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                tvCreatedTime.text = notification.createdAt?.toDate()?.let {
                    dateFormatter.format(it)
                } ?: ""

                // 읽음/안읽음 상태 표시
                unreadIndicator.visibility = if (isRead) View.GONE else View.VISIBLE

                // 읽음 버튼 상태 (이미 읽은 알림은 버튼 비활성화)
                btnMarkRead.isEnabled = !isRead
                btnMarkRead.text = if (isRead) "읽음" else "읽음 처리"
                btnMarkRead.alpha = if (isRead) 0.6f else 1.0f

                // 선택 상태 표시
                root.alpha = if (isSelected) 0.7f else (if (isRead) 0.8f else 1.0f)
                root.strokeWidth = if (isSelected) 4 else 0
                root.strokeColor = if (isSelected) priorityColor else Color.TRANSPARENT

                // 확장 상태
                expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
                btnMore.text = if (isExpanded) "접기" else "더보기"
                btnMore.setIconResource(
                    if (isExpanded) android.R.drawable.ic_menu_revert
                    else android.R.drawable.ic_menu_more
                )

                // 읽음 상태에 따른 스타일 조정
                if (isRead) {
                    tvNotificationTitle.alpha = 0.7f
                    tvNotificationContent.alpha = 0.7f
                    root.setCardBackgroundColor(Color.parseColor("#F5F5F5"))  // 읽은 알림은 회색 배경
                } else {
                    tvNotificationTitle.alpha = 1.0f
                    tvNotificationContent.alpha = 1.0f
                    root.setCardBackgroundColor(Color.WHITE)  // 안읽은 알림은 흰색 배경
                }

                // 클릭 리스너들
                root.setOnClickListener {
                    onItemClick(notification)
                }

                root.setOnLongClickListener {
                    toggleSelection(notification.id)
                    true
                }

                btnMore.setOnClickListener {
                    toggleExpansion(notification.id)
                    onMoreClick(notification, !isExpanded)
                }

                btnMarkRead.setOnClickListener {
                    if (!isRead) {  // 읽지 않은 알림만 읽음 처리 가능
                        onMarkReadClick(notification)
                    }
                }

                btnShare.setOnClickListener {
                    onShareClick(notification)
                }
            }
        }
    }

    private fun toggleSelection(notificationId: String) {
        if (notificationId in selectedItems) {
            selectedItems.remove(notificationId)
        } else {
            selectedItems.add(notificationId)
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == notificationId })
    }

    private fun toggleExpansion(notificationId: String) {
        if (notificationId in expandedItems) {
            expandedItems.remove(notificationId)
        } else {
            expandedItems.add(notificationId)
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == notificationId })
    }

    fun getSelectedIds(): List<String> = selectedItems.toList()

    fun getSelectedUnreadIds(): List<String> {
        return selectedItems.filter { id -> !isNotificationRead(id) }
    }

    fun clearSelection() {
        val previousSelected = selectedItems.toSet()
        selectedItems.clear()
        previousSelected.forEach { id ->
            val position = currentList.indexOfFirst { it.id == id }
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}