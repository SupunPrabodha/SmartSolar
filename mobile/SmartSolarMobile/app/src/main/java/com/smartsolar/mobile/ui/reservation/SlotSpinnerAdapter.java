package com.smartsolar.mobile.ui.reservation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse;
import com.smartsolar.mobile.util.ReservationUiUtils;
import java.util.ArrayList;
import java.util.List;

public final class SlotSpinnerAdapter extends BaseAdapter {
    private final Context context;
    private final List<AvailableSlotResponse> slots = new ArrayList<>();
    private final String promptText;

    public SlotSpinnerAdapter(Context context, List<AvailableSlotResponse> slots, String promptText) {
        this.context = context;
        if (slots != null) this.slots.addAll(slots);
        this.promptText = promptText != null ? promptText : "-- Select an active slot --";
    }

    @Override
    public int getCount() {
        return slots.isEmpty() ? 1 : slots.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0 || slots.isEmpty()) return null;
        return slots.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(context).inflate(R.layout.item_slot_spinner_selected, parent, false);

        TextView textSpinnerPrompt = view.findViewById(R.id.textSpinnerPrompt);
        View layoutSelectedSlot = view.findViewById(R.id.layoutSelectedSlot);
        TextView textSelectedStation = view.findViewById(R.id.textSelectedStation);
        TextView textSelectedBadge = view.findViewById(R.id.textSelectedBadge);
        TextView textSelectedSchedule = view.findViewById(R.id.textSelectedSchedule);
        TextView textSelectedSlotId = view.findViewById(R.id.textSelectedSlotId);

        if (position == 0 || slots.isEmpty()) {
            textSpinnerPrompt.setText(promptText);
            textSpinnerPrompt.setVisibility(View.VISIBLE);
            layoutSelectedSlot.setVisibility(View.GONE);
        } else {
            textSpinnerPrompt.setVisibility(View.GONE);
            layoutSelectedSlot.setVisibility(View.VISIBLE);
            AvailableSlotResponse slot = slots.get(position - 1);

            String station = slot.getStationId() != null && !slot.getStationId().trim().isEmpty()
                    ? slot.getStationId() : "Station";
            textSelectedStation.setText("Station " + ReservationUiUtils.shortReference(station));
            textSelectedBadge.setText(slot.getAvailableSlots() + " Avail");
            textSelectedSchedule.setText(ReservationUiUtils.formatUtc(slot.getStartAtUtc()));

            String slotIdSnippet = slot.getSlotId() != null && slot.getSlotId().length() > 8
                    ? slot.getSlotId().substring(0, 8) + "…"
                    : String.valueOf(slot.getSlotId());
            textSelectedSlotId.setText("Slot: " + slotIdSnippet);
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(context).inflate(R.layout.item_slot_spinner_dropdown, parent, false);

        TextView textDropdownPrompt = view.findViewById(R.id.textDropdownPrompt);
        View layoutDropdownSlot = view.findViewById(R.id.layoutDropdownSlot);
        TextView textDropdownStation = view.findViewById(R.id.textDropdownStation);
        TextView textDropdownBadge = view.findViewById(R.id.textDropdownBadge);
        TextView textDropdownSchedule = view.findViewById(R.id.textDropdownSchedule);
        TextView textDropdownSlotId = view.findViewById(R.id.textDropdownSlotId);

        if (position == 0 || slots.isEmpty()) {
            textDropdownPrompt.setText(promptText);
            textDropdownPrompt.setVisibility(View.VISIBLE);
            layoutDropdownSlot.setVisibility(View.GONE);
        } else {
            textDropdownPrompt.setVisibility(View.GONE);
            layoutDropdownSlot.setVisibility(View.VISIBLE);
            AvailableSlotResponse slot = slots.get(position - 1);

            String station = slot.getStationId() != null && !slot.getStationId().trim().isEmpty()
                    ? slot.getStationId() : "Station";
            textDropdownStation.setText("Station " + ReservationUiUtils.shortReference(station));
            textDropdownBadge.setText(slot.getAvailableSlots() + " Avail");

            String startFormatted = ReservationUiUtils.formatUtc(slot.getStartAtUtc());
            textDropdownSchedule.setText(startFormatted);
            textDropdownSlotId.setText("Slot ID: " + slot.getSlotId());
        }

        return view;
    }
}
