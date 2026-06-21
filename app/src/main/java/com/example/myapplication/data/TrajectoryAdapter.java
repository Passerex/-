package com.example.myapplication.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.Trajectory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrajectoryAdapter extends RecyclerView.Adapter<TrajectoryAdapter.ViewHolder> {

    private final List<Trajectory> list;
    private final OnItemClickListener listener;
    private final boolean showPlayButton;
    private String nowPlayingName;
    private String playingProgressText;
    private int playingCurrent = 0;
    private int playingTotal = 0;

    public interface OnItemClickListener {
        void onItemClick(Trajectory trajectory);
        void onPlayClick(Trajectory trajectory);
        void onRenameClick(Trajectory trajectory);
        void onDeleteClick(Trajectory trajectory);
    }

    public TrajectoryAdapter(List<Trajectory> list, OnItemClickListener listener, boolean showPlayButton) {
        this.list = list;
        this.listener = listener;
        this.showPlayButton = showPlayButton;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trajectory, parent, false);
        return new ViewHolder(v);
    }

    /** 设置当前正在播放的轨迹名称，刷新列表指示器 */
    public void setNowPlaying(String name) {
        nowPlayingName = name;
        if (name == null) {
            playingProgressText = null;
            playingCurrent = 0;
            playingTotal = 0;
        }
        notifyDataSetChanged();
    }

    /** 更新播放进度（每播一个点调用） */
    public void updatePlayingProgress(int current, int total) {
        playingCurrent = current;
        playingTotal = total;
        playingProgressText = current + "/" + total;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Trajectory t = list.get(position);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        holder.tvName.setText(t.name);
        holder.tvDate.setText(sdf.format(new Date(t.startTime)));
        holder.tvInfo.setText(String.format(Locale.getDefault(),
                "%d 个点 · %s · %s",
                t.getPointCount(), t.getFormattedDistance(), t.getFormattedDuration()));

        // 播放状态条（底部，仅在播放时显示）
        boolean isPlaying = t.name.equals(nowPlayingName);
        if (isPlaying && playingProgressText != null) {
            holder.layoutPlayingBar.setVisibility(View.VISIBLE);
            holder.tvPlayingProgress.setText(playingProgressText);
        } else {
            holder.layoutPlayingBar.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(t));

        holder.btnRename.setOnClickListener(v -> listener.onRenameClick(t));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(t));

        if (showPlayButton) {
            holder.btnPlay.setVisibility(View.VISIBLE);
            holder.btnPlay.setOnClickListener(v -> listener.onPlayClick(t));
        } else {
            holder.btnPlay.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvInfo, tvPlayingProgress;
        View btnRename, btnDelete, btnPlay, layoutPlayingBar;
        ImageView ivPlaying;

        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_trajectory_name);
            tvDate = v.findViewById(R.id.tv_trajectory_date);
            tvInfo = v.findViewById(R.id.tv_trajectory_info);
            tvPlayingProgress = v.findViewById(R.id.tv_playing_progress);
            ivPlaying = v.findViewById(R.id.iv_playing_indicator);
            layoutPlayingBar = v.findViewById(R.id.layout_playing_bar);
            btnRename = v.findViewById(R.id.btn_trajectory_rename);
            btnDelete = v.findViewById(R.id.btn_trajectory_delete);
            btnPlay = v.findViewById(R.id.btn_trajectory_play);
        }
    }
}
