package com.smartsolar.mobile;
import com.smartsolar.mobile.util.DisplayReference;
import java.util.Arrays;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
public class DisplayReferenceTest {
    private static final String[][] FIXTURES = {
        {"11111111-1111-1111-1111-111111111111", "SB2J5ZFT6T"},
        {"22222222222222222222222222222222", "8FHMZDSSO5"},
        {"3ef796dd-1234-5678-9abc-12345678d770", "FUOFG2HQ7M"}
    };
    @Test public void identicalWebFixturesAndPrefixesAcrossLocaleAndCanonicalForms() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            for (String[] f : FIXTURES) {
                assertEquals("REF-"+f[1], DisplayReference.reservation(f[0]));
                assertEquals("STN-"+f[1], DisplayReference.station(f[0]));
                assertEquals("SLOT-"+f[1], DisplayReference.slot(f[0]));
                assertEquals("REF-"+f[1], DisplayReference.reservation(" "+f[0].toUpperCase(Locale.ROOT).replace("-","")+" "));
            }
            for(int index=FIXTURES.length-1;index>=0;index--)
                assertEquals("REF-"+FIXTURES[index][1],DisplayReference.reservation(FIXTURES[index][0]));
            assertNotEquals(DisplayReference.reservation(FIXTURES[0][0]),DisplayReference.reservation(FIXTURES[1][0]));
        } finally { Locale.setDefault(previous); }
    }
    @Test public void invalidIdentifiersAreSafe() {
        for(String value:new String[]{null,"","not-a-guid","00000000-0000-0000-0000-000000000000","123"})
            assertEquals("Unavailable",DisplayReference.reservation(value));
    }
    @Test public void slotInputResolvesOnlyAnAuthorizedLoadedReferenceToTheUnchangedId() {
        String id=FIXTURES[0][0];
        assertEquals(id,DisplayReference.resolveSlot("slot-sb2j5zft6t",Arrays.asList(id,id)));
        assertEquals("",DisplayReference.resolveSlot("SLOT-8FHMZDSSO5",Arrays.asList(id)));
        assertEquals("",DisplayReference.resolveSlot("Unavailable",Arrays.asList("invalid")));
        assertEquals("",DisplayReference.resolveSlot("",Arrays.asList(id)));
    }
}
