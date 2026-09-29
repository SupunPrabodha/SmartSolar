/*
 * SmartSolar Mobile - Prosumer & Operator Solar Energy Management Platform
 * ReservationAdapter.java - RecyclerView adapter for displaying reservation cards with actions
 */

package com.smartsolar.mobile.ui.reservations;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.reservation.ModifyReservationActivity;
import com.smartsolar.mobile.ui.reservation.ReservationSummaryActivity;
import com.smartsolar.mobile.util.ReservationUiUtils;
import com.smartsolar.mobile.util.StationNameResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Adapter for rendering reservation list cards across Active, Pending, History, and Search tabs,
 * supporting expandable details, modification, cancellation, QR viewing, and directions.
 */
public final class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ViewHolder> {
    private static final Gson GSON = new Gson();

    public interface OnItemClickListener {
        void onItemClick(ReservationResponse reservation);
    }

    private final List<ReservationResponse> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final boolean allowQrIssuance;
    private final Set<String> expandedIds = new HashSet<>();

    public ArrayList<String> expanded() {
        return new ArrayList<>(expandedIds);
    }

    public void restoreExpanded(List<String> ids) {
        if (ids != null) {
            expandedIds.addAll(ids);
        }
    }

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
                .inflate(R.layout.item_reservation_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReservationResponse item = items.get(position);
        holder.bind(item, listener, expandedIds);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private final View viewStatusAccent;
        private final View cardHeader;

        private final TextView textCardEnergy;
        private final TextView textCardStatus;
        private final TextView textCardStation;
        private final ImageView textChevron;

        private final View layoutExpandedDetails;
        private final TextView textExpandedStart;
        private final TextView textExpandedEnd;
        private final TextView textExpandedCutoff;

        private final View layoutRejectionNotice;
        private final TextView textRejectionRemark;
        private final TextView textExpandedRestriction;

        private final Button buttonCardModify;
        private final Button buttonCardCancel;

        private final View layoutCardApprovedActions;
        private final Button buttonCardViewQr;
        private final Button buttonCardGetDirections;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            viewStatusAccent = itemView.findViewById(R.id.viewStatusAccent);
            cardHeader = itemView.findViewById(R.id.cardHeader);

            textCardEnergy = itemView.findViewById(R.id.textCardEnergy);
            textCardStatus = itemView.findViewById(R.id.textCardStatus);
            textCardStation = itemView.findViewById(R.id.textCardStation);
            textChevron = itemView.findViewById(R.id.textChevron);

            layoutExpandedDetails = itemView.findViewById(R.id.layoutExpandedDetails);
            textExpandedStart = itemView.findViewById(R.id.textExpandedStart);
            textExpandedEnd = itemView.findViewById(R.id.textExpandedEnd);
            textExpandedCutoff = itemView.findViewById(R.id.textExpandedCutoff);

            layoutRejectionNotice = itemView.findViewById(R.id.layoutRejectionNotice);
            textRejectionRemark = itemView.findViewById(R.id.textRejectionRemark);
            textExpandedRestriction = itemView.findViewById(R.id.textExpandedRestriction);

            buttonCardModify = itemView.findViewById(R.id.buttonCardModify);
            buttonCardCancel = itemView.findViewById(R.id.buttonCardCancel);

            layoutCardApprovedActions = itemView.findViewById(R.id.layoutCardApprovedActions);
            buttonCardViewQr = itemView.findViewById(R.id.buttonCardViewQr);
            buttonCardGetDirections = itemView.findViewById(R.id.buttonCardGetDirections);
        }

        public void bind(
                ReservationResponse item,
                OnItemClickListener listener,
                Set<String> expandedIds
        ) {
            Context context = itemView.getContext();
            long nowMillis = System.currentTimeMillis();

            // Bind essential preview information.
            textCardStatus.setText(item.getStatus());
            ReservationUiUtils.formatStatusBadge(textCardStatus, item.getStatus());
            ReservationUiUtils.formatStatusAccent(viewStatusAccent, item.getStatus());

            StationNameResolver.bindStationName(textCardStation, item.getStationId());

            textCardEnergy.setText(
                    String.format(Locale.US, "%.1f kWh", item.getEnergyAmountKwh())
            );

            // Bind expanded details.
            textExpandedStart.setText(
                    ReservationUiUtils.formatUtc(item.getScheduledStartAtUtc())
            );

            textExpandedEnd.setText(
                    ReservationUiUtils.formatUtc(item.getScheduledEndAtUtc())
            );

            textExpandedCutoff.setText(
                    ReservationUiUtils.formatCutoffUtc(item.getScheduledStartAtUtc())
            );

            // Show rejection remark only for rejected reservations.
            if ("Rejected".equalsIgnoreCase(item.getStatus())
                    && item.getRejectionRemark() != null
                    && !item.getRejectionRemark().trim().isEmpty()) {

                textRejectionRemark.setText(item.getRejectionRemark());
                layoutRejectionNotice.setVisibility(View.VISIBLE);
            } else {
                layoutRejectionNotice.setVisibility(View.GONE);
            }

            // Preserve reservation action restrictions.
            boolean terminal =
                    ReservationUiUtils.isTerminalStatus(item.getStatus());

            boolean cutoffPassed =
                    ReservationUiUtils.isCutoffPassed(
                            item.getScheduledStartAtUtc(),
                            nowMillis
                    );

            if (terminal) {
                textExpandedRestriction.setText(R.string.terminal_status_notice);
                textExpandedRestriction.setVisibility(View.VISIBLE);

                buttonCardModify.setEnabled(false);
                buttonCardCancel.setEnabled(false);

            } else if (cutoffPassed) {
                textExpandedRestriction.setText(R.string.cutoff_passed_notice);
                textExpandedRestriction.setVisibility(View.VISIBLE);

                buttonCardModify.setEnabled(false);
                buttonCardCancel.setEnabled(false);

            } else {
                textExpandedRestriction.setVisibility(View.GONE);

                buttonCardModify.setEnabled(true);
                buttonCardCancel.setEnabled(true);
            }

            // Approved reservation actions.
            boolean approved =
                    "Approved".equalsIgnoreCase(item.getStatus());

            if (layoutCardApprovedActions != null) {
                layoutCardApprovedActions.setVisibility(
                        approved ? View.VISIBLE : View.GONE
                );
            }

            buttonCardViewQr.setVisibility(
                    approved ? View.VISIBLE : View.GONE
            );

            buttonCardViewQr.setOnClickListener(
                    v -> openQrScreen(context, item)
            );

            buttonCardGetDirections.setVisibility(
                    approved ? View.VISIBLE : View.GONE
            );

            buttonCardGetDirections.setOnClickListener(
                    v -> openDirectionsForStation(
                            context,
                            item.getStationId()
                    )
            );

            // Modify reservation.
            buttonCardModify.setOnClickListener(v -> {
                Intent intent =
                        new Intent(context, ModifyReservationActivity.class);

                intent.putExtra(
                        ModifyReservationActivity.EXTRA_RESERVATION_JSON,
                        GSON.toJson(item)
                );

                context.startActivity(intent);
            });

            // Cancel reservation.
            buttonCardCancel.setOnClickListener(
                    v -> showCancelConfirmDialog(context, item)
            );

            // Restore expanded/collapsed state.
            boolean isExpanded =
                    expandedIds.contains(item.getReservationId());

            layoutExpandedDetails.setVisibility(
                    isExpanded ? View.VISIBLE : View.GONE
            );

            // UI polish: rotate vector chevron instead of using Unicode arrows.
            if (textChevron != null) {
                textChevron.setRotation(
                        isExpanded ? 180f : 0f
                );
            }

            androidx.core.view.ViewCompat.setStateDescription(
                    cardHeader,
                    context.getString(
                            isExpanded
                                    ? R.string.details_expanded
                                    : R.string.details_collapsed
                    )
            );

            // Expand/collapse interaction.
            cardHeader.setOnClickListener(v -> {
                boolean expandedNow =
                        layoutExpandedDetails.getVisibility() == View.VISIBLE;

                if (expandedNow) {
                    expandedIds.remove(item.getReservationId());
                } else {
                    expandedIds.add(item.getReservationId());
                }

                layoutExpandedDetails.setVisibility(
                        expandedNow ? View.GONE : View.VISIBLE
                );

                if (textChevron != null) {
                    textChevron.setRotation(
                            expandedNow ? 0f : 180f
                    );
                }

                androidx.core.view.ViewCompat.setStateDescription(
                        cardHeader,
                        context.getString(
                                expandedNow
                                        ? R.string.details_collapsed
                                        : R.string.details_expanded
                        )
                );

                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        private static void openQrScreen(
                Context context,
                ReservationResponse item
        ) {
            Intent intent =
                    new Intent(context, ReservationQrActivity.class);

            intent.putExtra(
                    ReservationQrActivity.EXTRA_RESERVATION_ID,
                    item.getReservationId()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_PROSUMER_NIC,
                    item.getProsumerNic()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_STATION_ID,
                    item.getStationId()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_SLOT_ID,
                    item.getSlotId()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_ENERGY_AMOUNT,
                    item.getEnergyAmountKwh()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_SCHEDULE_START,
                    item.getScheduledStartAtUtc()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_SCHEDULE_END,
                    item.getScheduledEndAtUtc()
            );

            intent.putExtra(
                    ReservationQrActivity.EXTRA_STATUS,
                    item.getStatus()
            );

            context.startActivity(intent);
        }

        private static void openDirectionsForStation(
                Context context,
                String stationId
        ) {
            if (stationId == null || stationId.trim().isEmpty()) {
                return;
            }

            ApiService api =
                    RetrofitClient.create(
                            context,
                            BuildConfig.API_BASE_URL,
                            BuildConfig.DEBUG
                    );

            api.getStation(stationId)
                    .enqueue(new Callback<StationResponse>() {

                        @Override
                        public void onResponse(
                                Call<StationResponse> call,
                                Response<StationResponse> response
                        ) {
                            StationResponse station = response.body();
                            Uri uri;

                            if (station != null
                                    && (station.latitude != 0
                                    || station.longitude != 0)) {

                                String label =
                                        station.name != null
                                                && !station.name.isEmpty()
                                                ? station.name
                                                : "Solar Station " + stationId;

                                uri = Uri.parse(
                                        "geo:"
                                                + station.latitude
                                                + ","
                                                + station.longitude
                                                + "?q="
                                                + station.latitude
                                                + ","
                                                + station.longitude
                                                + "("
                                                + Uri.encode(label)
                                                + ")"
                                );

                            } else if (station != null
                                    && station.address != null
                                    && !station.address.isEmpty()) {

                                uri = Uri.parse(
                                        "geo:0,0?q="
                                                + Uri.encode(station.address)
                                );

                            } else {

                                uri = Uri.parse(
                                        "geo:0,0?q="
                                                + Uri.encode(
                                                "Solar Station " + stationId
                                        )
                                );
                            }

                            Intent mapIntent =
                                    new Intent(Intent.ACTION_VIEW, uri);

                            try {
                                context.startActivity(mapIntent);

                            } catch (android.content.ActivityNotFoundException e) {

                                String query =
                                        station != null
                                                && (station.latitude != 0
                                                || station.longitude != 0)
                                                ? station.latitude
                                                + ","
                                                + station.longitude
                                                : Uri.encode(
                                                station != null
                                                        && station.address != null
                                                        && !station.address.isEmpty()
                                                        ? station.address
                                                        : "Solar Station "
                                                        + stationId
                                        );

                                String webUrl =
                                        "https://www.google.com/maps/search/?api=1&query="
                                                + query;

                                context.startActivity(
                                        new Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(webUrl)
                                        )
                                );
                            }
                        }

                        @Override
                        public void onFailure(
                                Call<StationResponse> call,
                                Throwable t
                        ) {
                            Intent mapIntent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "geo:0,0?q="
                                                            + Uri.encode(
                                                            "Solar Station "
                                                                    + stationId
                                                    )
                                            )
                                    );

                            try {
                                context.startActivity(mapIntent);

                            } catch (android.content.ActivityNotFoundException e) {

                                context.startActivity(
                                        new Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                        "https://www.google.com/maps/search/?api=1&query="
                                                                + Uri.encode(
                                                                "Solar Station "
                                                                        + stationId
                                                        )
                                                )
                                        )
                                );
                            }
                        }
                    });
        }

        private static void showCancelConfirmDialog(
                Context context,
                ReservationResponse reservation
        ) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.dialog_cancel_title)
                    .setMessage(
                            context.getString(
                                    R.string.dialog_cancel_message,
                                    reservation.getReservationId(),
                                    String.format(
                                            Locale.US,
                                            "%.1f",
                                            reservation.getEnergyAmountKwh()
                                    )
                            )
                    )
                    .setNegativeButton(
                            R.string.dialog_keep_reservation,
                            null
                    )
                    .setPositiveButton(
                            R.string.dialog_confirm_cancellation,
                            (dialog, which) ->
                                    executeCancel(context, reservation)
                    )
                    .show();
        }

        private static void executeCancel(
                Context context,
                ReservationResponse reservation
        ) {
            ApiService api =
                    RetrofitClient.create(
                            context,
                            BuildConfig.API_BASE_URL,
                            BuildConfig.DEBUG
                    );

            new ReservationRepository(api)
                    .cancelReservation(
                            reservation.getReservationId(),
                            new ReservationRepository.Callback<ReservationResponse>() {

                                @Override
                                public void onSuccess(
                                        ReservationResponse result
                                ) {
                                    Intent intent =
                                            new Intent(
                                                    context,
                                                    ReservationSummaryActivity.class
                                            );

                                    intent.putExtra(
                                            ReservationSummaryActivity.EXTRA_RESERVATION_JSON,
                                            GSON.toJson(result)
                                    );

                                    intent.putExtra(
                                            ReservationSummaryActivity.EXTRA_MODE,
                                            ReservationSummaryActivity.MODE_CANCELLED
                                    );

                                    context.startActivity(intent);
                                }

                                @Override
                                public void onError(
                                        ReservationError error
                                ) {
                                    Toast.makeText(
                                            context,
                                            error.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            }
                    );
        }
    }
}