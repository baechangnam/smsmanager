package apps.kr.smsmanager.ui;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import apps.kr.smsmanager.R;
import apps.kr.smsmanager.model.MsgItem;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.VH> {

    private final List<MsgItem> items = new ArrayList<>();

    public interface OnDeleteClickListener {
        void onDeleteClick(MsgItem item);
    }

    private OnItemClickListener clickListener;
    private OnDeleteClickListener deleteListener;

    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { this.deleteListener = l; }

    public void setItems(List<MsgItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public MsgItem getItem(int pos) {
        if (pos < 0 || pos >= items.size()) return null;
        return items.get(pos);
    }

    public List<MsgItem> getItems() {
        return new ArrayList<>(items);
    }


    public interface OnItemClickListener {
        void onItemClick(MsgItem item);
    }



    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_sms, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MsgItem it = items.get(position);
        h.addr.setText(it.address == null ? "(알수없음)" : it.address);

        if (it.body != null && !it.body.isEmpty()) {
            h.body.setText(it.body);
        } else if (it.isMms) {
            h.body.setText("[MMS]");
        } else {
            h.body.setText("");
        }

        String dateStr = DateFormat.format("yyyy-MM-dd HH:mm", it.date).toString();
        h.date.setText(dateStr);

        String msgType = it.isMms ? "MMS" : "SMS";
        String dir     = (it.box == 2) ? "발신" : "수신";   // 1: 수신, 2: 발신

        h.type.setText(msgType + " · " + dir);             // 예: "SMS · 발신"

        // 클릭/삭제 리스너 동일
        h.fgContent.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(it);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(it);
        });

        h.fgContent.setTranslationX(0);
    }

    public void removeAt(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        items.remove(pos);
        notifyItemRemoved(pos);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView addr, body, date, type;
        TextView btnDelete;
        View fgContent;
        public VH(@NonNull View itemView) {
            super(itemView);
            fgContent = itemView.findViewById(R.id.fgContent);
            addr = itemView.findViewById(R.id.tvAddr);
            body = itemView.findViewById(R.id.tvBody);
            date = itemView.findViewById(R.id.tvDate);
            type = itemView.findViewById(R.id.tvType);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
