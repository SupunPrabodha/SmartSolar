package com.smartsolar.mobile.ui.reservation;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import com.smartsolar.mobile.util.DisplayReference;
import java.util.ArrayList;
import java.util.List;

/** Presentation suggestions retain their real IDs only in the caller's authorized slot list. */
final class SlotReferenceInput {
    private SlotReferenceInput() { }
    static void bind(EditText field, Iterable<String> ids) {
        List<String> labels = new ArrayList<>();
        for (String id : ids) {
            String label = DisplayReference.slot(id);
            if (!"Unavailable".equals(label) && !labels.contains(label)) labels.add(label);
        }
        AutoCompleteTextView input = (AutoCompleteTextView) field;
        input.setAdapter(new ArrayAdapter<>(field.getContext(), android.R.layout.simple_dropdown_item_1line, labels));
        input.setThreshold(1);
        input.setOnClickListener(view -> input.showDropDown());
    }
}
