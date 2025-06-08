// app/src/main/java/com/jjangdol/biorhythm/ui/main/NotificationFragment.kt
package com.jjangdol.biorhythm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentNotificationBinding
import com.jjangdol.biorhythm.data.model.Notification
import com.jjangdol.biorhythm.data.model.NotificationPriority
import com.jjangdol.biorhythm.vm.UserNotificationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationFragment : Fragment(R.layout.fragment_notification) {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserNotificationViewModel by viewModels()
    private lateinit var notificationAdapter: UserNotificationAdapter

    private var filterPriority: NotificationPriority? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNotificationBinding.bind(view)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }


    private fun setupRecyclerView() {
        notificationAdapter = UserNotificationAdapter(
            onItemClick = { notification ->
                viewModel.markAsRead(notification.id)
                showNotificationDetail(notification)
            },
            onMoreClick = { notification, isExpanded ->
                // 확장/축소 처리는 어댑터에서 자동 처리
            },
            onMarkReadClick = { notification ->
                viewModel.markAsRead(notification.id)
                // 즉시 어댑터 갱신
                notificationAdapter.notifyDataSetChanged()
            },
            onShareClick = { notification ->
                shareNotification(notification)
            },
            isNotificationRead = { notificationId ->
                viewModel.isNotificationRead(notificationId)
            }
        )

        binding.recyclerViewNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }

    private fun setupClickListeners() {
        // 필터 칩 그룹
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filterPriority = when (checkedIds.firstOrNull()) {
                R.id.chipHigh -> NotificationPriority.HIGH
                R.id.chipNormal -> NotificationPriority.NORMAL
                R.id.chipLow -> NotificationPriority.LOW
                else -> null
            }
            viewModel.setFilter(filterPriority)
        }

        // 새로고침 버튼
        binding.btnRefresh.setOnClickListener {
            viewModel.refreshNotifications()
            Toast.makeText(requireContext(), "알림을 새로고침했습니다", Toast.LENGTH_SHORT).show()
        }

        // 모두 읽음 처리 버튼
        binding.btnMarkAllRead.setOnClickListener {
            showMarkAllReadDialog()
        }

        // 선택 읽음 처리 버튼
        binding.btnMarkSelectedRead.setOnClickListener {
            val selectedIds = notificationAdapter.getSelectedUnreadIds()  // 읽지 않은 것만
            if (selectedIds.isNotEmpty()) {
                viewModel.markMultipleAsRead(selectedIds)
                notificationAdapter.clearSelection()
                binding.bottomActionLayout.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "읽음 처리할 알림을 선택하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collectLatest { notifications ->
                notificationAdapter.submitList(notifications)

                // 빈 상태 처리
                binding.emptyLayout.visibility =
                    if (notifications.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unreadCount.collectLatest { count ->
                binding.tvNotificationSummary.text = "읽지 않은 알림이 ${count}개 있습니다"

                // 모두 읽음 버튼 활성화/비활성화
                binding.btnMarkAllRead.isEnabled = count > 0
            }
        }

        // 🔥 읽음 상태 변경 관찰 추가 - 이게 핵심!
        viewLifecycleOwner.lifecycleScope.launch {
            // UserNotificationRepository의 readNotificationIds Flow 관찰
            // (실제로는 UserNotificationViewModel을 통해 접근해야 함)
            // 임시로 UI 상태 변경을 통해 갱신
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is UserNotificationViewModel.UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is UserNotificationViewModel.UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        if (state.message.isNotEmpty()) {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            // 🔥 성공 메시지가 있을 때 어댑터 갱신
                            notificationAdapter.notifyDataSetChanged()
                        }
                    }
                    is UserNotificationViewModel.UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showNotificationDetail(notification: Notification) {
        AlertDialog.Builder(requireContext())
            .setTitle("${notification.priority.displayName} 알림")
            .setMessage(notification.content)
            .setPositiveButton("확인", null)
            .setNeutralButton("공유") { _, _ ->
                shareNotification(notification)
            }
            .show()
    }

    private fun shareNotification(notification: Notification) {
        val shareText = "${notification.title}\n\n${notification.content}\n\n- ${notification.priority.displayName} 알림"

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, notification.title)
        }

        startActivity(Intent.createChooser(shareIntent, "알림 공유"))
    }

    private fun showMarkAllReadDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("모든 알림 읽음 처리")
            .setMessage("모든 알림을 읽음으로 처리하시겠습니까?")
            .setPositiveButton("읽음 처리") { _, _ ->
                viewModel.markAllAsRead()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}