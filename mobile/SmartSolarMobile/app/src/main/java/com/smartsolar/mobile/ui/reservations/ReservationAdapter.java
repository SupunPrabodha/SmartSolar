package com.smartsolar.mobile.ui.reservations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import java.util.ArrayList;
import java.util.List;

public final class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(ReservationResponse reservation);
    }

    private final List<ReservationResponse> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public ReservationAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReservationResponse> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reservation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReservationResponse item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textId;
        private final TextView textStatus;
        private final TextView textProsumer;
        private final TextView textStationSlot;
        private final TextView textSchedule;
        private final TextView textEnergy;
        private final View buttonViewQr;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textId = itemView.findViewById(R.id.textItemReservationId);
            textStatus = itemView.findViewById(R.id.textItemStatus);
            textProsumer = itemView.findViewById(R.id.textItemProsumer);
            textStationSlot = itemView.findViewById(R.id.textItemStationSlot);
            textSchedule = itemView.findViewById(R.id.textItemSchedule);
            textEnergy = itemView.findViewById(R.id.textItemEnergy);
            buttonViewQr = itemView.findViewById(R.id.buttonItemViewQr);
        }

        public void bind(ReservationResponse item, OnItemClickListener listener) {
            textId.setText(itemView.getContext().getString(R.string.reservation_id_label, item.getReservationId()));
            textStatus.setText(item.getStatus() != null ? item.getStatus() : "");
            textProsumer.setText(itemView.getContext().getString(R.string.prosumer_nic_label, item.getProsumerNic()));
            textStationSlot.setText(itemView.getContext().getString(
                    R.string.station_slot_label,
                    item.getStationId(),
                    item.getSlotId()
            ));

            String start = item.getScheduledStartAtUtc() != null ? item.getScheduledStartAtUtc() : "";
            String end = item.getScheduledEndAtUtc() != null ? item.getScheduledEndAtUtc() : "";
            textSchedule.setText(itemView.getContext().getString(R.string.schedule_label, start, end));

            textEnergy.setText(itemView.getContext().getString(R.string.energy_label, item.getEnergyAmountKwh()));

            boolean isApproved = item.getStatus() != null && item.getStatus().equalsIgnoreCase("Approved");
            if (buttonViewQr != null) {
                buttonViewQr.setVisibility(isApproved ? View.VISIBLE : View.GONE);
                buttonViewQr.setOnClickListener(v -> openQrScreen(v.getContext(), item));
            }

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClick(item));
            } else if (isApproved) {
                itemView.setOnClickListener(v -> openQrScreen(v.getContext(), item));
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private static void openQrScreen(android.content.Context context, ReservationResponse item) {
            android.content.Intent intent = new android.content.Intent(context, ReservationQrActivity.class);
            intent.putExtra(ReservationQrActivity.EXTRA_RESERVATION_ID, item.getReservationId());
            intent.putExtra(ReservationQrActivity.EXTRA_PROSUMER_NIC, item.getProsumerNic());
            intent.putExtra(ReservationQrActivity.EXTRA_STATION_ID, item.getStationId());
            intent.putExtra(ReservationQrActivity.EXTRA_SLOT_ID, item.getSlotId());
            intent.putExtra(ReservationQrActivity.EXTRA_ENERGY_AMOUNT, item.getEnergyAmountKwh());
            intent.putExtra(ReservationQrActivity.EXTRA_SCHEDULE_START, item.getScheduledStartAtUtc());
            intent.putExtra(ReservationQrActivity.EXTRA_SCHEDULE_END, item.getScheduledEndAtUtc());
            intent.putExtra(ReservationQrActivity.EXTRA_STATUS, item.getStatus());
            context.startActivity(intent);
        }
    }
}
