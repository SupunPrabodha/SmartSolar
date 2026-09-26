package com.smartsolar.mobile.ui.reservations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.util.ReservationUiUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(ReservationResponse reservation);
    }

    private final List<ReservationResponse> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final boolean allowQrIssuance;

    public ReservationAdapter(OnItemClickListener listener) {
        this(listener, false);
    }

    public ReservationAdapter(OnItemClickListener listener, boolean allowQrIssuance) {
        this.listener = listener;
        this.allowQrIssuance = allowQrIssuance;
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
        holder.bind(item, listener, allowQrIssuance);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private final View cardHeader;
        private final TextView textId;
        private final TextView textStatus;
        private final TextView textStationSlot;
        private final TextView textEnergy;
        private final TextView textSchedule;
        private final TextView textChevron;

        private final View layoutExpandedDetails;
        private final TextView textExpandedReservationId;
        private final TextView textExpandedSlotId;
        private final TextView textExpandedStart;
        private final TextView textExpandedEnd;
        private final TextView textProsumer;
        private final Button buttonViewQr;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader = itemView.findViewById(R.id.cardHeader);
            textId = itemView.findViewById(R.id.textItemReservationId);
            textStatus = itemView.findViewById(R.id.textItemStatus);
            textStationSlot = itemView.findViewById(R.id.textItemStationSlot);
            textEnergy = itemView.findViewById(R.id.textItemEnergy);
            textSchedule = itemView.findViewById(R.id.textItemSchedule);
            textChevron = itemView.findViewById(R.id.textChevron);

            layoutExpandedDetails = itemView.findViewById(R.id.layoutExpandedDetails);
            textExpandedReservationId = itemView.findViewById(R.id.textExpandedReservationId);
            textExpandedSlotId = itemView.findViewById(R.id.textExpandedSlotId);
            textExpandedStart = itemView.findViewById(R.id.textExpandedStart);
            textExpandedEnd = itemView.findViewById(R.id.textExpandedEnd);
            textProsumer = itemView.findViewById(R.id.textItemProsumer);
            buttonViewQr = itemView.findViewById(R.id.buttonItemViewQr);
        }

        public void bind(ReservationResponse item, OnItemClickListener listener, boolean allowQrIssuance) {
            textId.setText("Reservation #" + ReservationUiUtils.shortReference(item.getReservationId()));

            ReservationUiUtils.formatStatusBadge(textStatus, item.getStatus());

            String station = item.getStationId() != null ? item.getStationId() : "—";
            textStationSlot.setText("Station " + ReservationUiUtils.shortReference(station));

            textEnergy.setText(String.format(Locale.US, "%.1f kWh", item.getEnergyAmountKwh()));

            String startFormatted = ReservationUiUtils.formatUtc(item.getScheduledStartAtUtc());
            textSchedule.setText(ReservationUiUtils.schedule(item.getScheduledStartAtUtc(), item.getScheduledEndAtUtc()));

            // Bind Expanded Details
            if (textExpandedReservationId != null) {
                textExpandedReservationId.setText(item.getReservationId() != null ? item.getReservationId() : "—");
            }
            if (textExpandedSlotId != null) {
                textExpandedSlotId.setText(item.getSlotId() != null ? item.getSlotId() : "—");
            }
            if (textExpandedStart != null) {
                textExpandedStart.setText(startFormatted);
            }
            if (textExpandedEnd != null) {
                textExpandedEnd.setText(ReservationUiUtils.formatUtc(item.getScheduledEndAtUtc()));
            }
            if (textProsumer != null) {
                if (item.getProsumerNic() != null && !item.getProsumerNic().trim().isEmpty()) {
                    textProsumer.setText(itemView.getContext().getString(R.string.prosumer_nic_label, item.getProsumerNic()));
                    textProsumer.setVisibility(View.VISIBLE);
                } else {
                    textProsumer.setVisibility(View.GONE);
                }
            }

            boolean isApproved = allowQrIssuance && item.getStatus() != null && item.getStatus().equalsIgnoreCase("Approved");
            if (buttonViewQr != null) {
                buttonViewQr.setVisibility(isApproved ? View.VISIBLE : View.GONE);
                buttonViewQr.setOnClickListener(v -> openQrScreen(v.getContext(), item));
            }

            // Default collapsed state
            if (layoutExpandedDetails != null) {
                layoutExpandedDetails.setVisibility(View.GONE);
            }
            if (textChevron != null) {
                textChevron.setText("⌄");
            }

            // Expand/Collapse interaction matching Member 3's card
            if (cardHeader != null) {
                androidx.core.view.ViewCompat.setStateDescription(cardHeader, itemView.getContext().getString(R.string.details_collapsed));
                cardHeader.setOnClickListener(v -> {
                    if (layoutExpandedDetails != null) {
                        boolean isExpanded = layoutExpandedDetails.getVisibility() == View.VISIBLE;
                        androidx.core.view.ViewCompat.setStateDescription(cardHeader, itemView.getContext().getString(isExpanded ? R.string.details_collapsed : R.string.details_expanded));
                        layoutExpandedDetails.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                        if (textChevron != null) {
                            textChevron.setText(isExpanded ? "⌄" : "⌃");
                        }
                    }
                    if (listener != null) {
                        listener.onItemClick(item);
                    }
                });
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
