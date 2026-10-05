package com.smartsolar.mobile;
import com.smartsolar.mobile.ui.reservations.ReferenceSearch;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.util.DisplayReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReferenceSearchTest {
    private static final String ID="11111111-1111-1111-1111-111111111111";
    private static final String STATION="22222222222222222222222222222222";
    private static ReservationResponse row() { return new ReservationResponse(ID,"200012345678",STATION,ID,1,
        "2030-01-01T00:00:00Z","2030-01-01T01:00:00Z","Pending","",""); }
    @Test public void resolvesAcrossPagesAndSendsOnlyOriginalIdsWithOriginalFilters() {
        Map<String,String> filters=new HashMap<>();
        filters.put("reservationId",DisplayReference.reservation(ID));filters.put("stationId",DisplayReference.station(STATION));
        filters.put("status","Pending"); filters.put("page","3"); filters.put("pageSize","20");
        List<Map<String,String>> calls=new ArrayList<>();
        AtomicReference<ReservationPageResponse> actual=new AtomicReference<>();
        ReferenceSearch.search((query,callback)->{
            calls.add(new HashMap<>(query));
            assertEquals("Pending",query.get("status"));
            if(calls.size()==1) callback.onComplete(new ReservationPageResponse(Collections.emptyList(),1,100,true),0,200);
            else callback.onComplete(new ReservationPageResponse(Collections.singletonList(row()),2,100,false),0,200);
        },filters,(result,error,code)->actual.set(result));
        assertEquals(3,calls.size());assertEquals("2",calls.get(1).get("page"));
        assertFalse(calls.get(0).containsKey("reservationId"));
        assertEquals(ID,calls.get(2).get("reservationId"));assertEquals(STATION,calls.get(2).get("stationId"));
        assertEquals("3",calls.get(2).get("page"));
        assertEquals("20",calls.get(2).get("pageSize"));assertEquals(ID,actual.get().getItems().get(0).getReservationId());
        assertEquals(DisplayReference.reservation(ID),filters.get("reservationId"));
    }
    @Test public void missingReferenceReturnsEmptyRatherThanSendingDisplayValue() {
        AtomicInteger calls=new AtomicInteger();
        ReferenceSearch.search((query,callback)->{calls.incrementAndGet();callback.onComplete(new ReservationPageResponse(),0,200);},
            Collections.singletonMap("reservationId",DisplayReference.reservation(ID)),(result,error,code)->{
                assertTrue(result.getItems().isEmpty());assertEquals(200,code);
            });
        assertEquals(1,calls.get());
    }
    @Test public void invalidReferenceDoesNotMakeARequestAnd401IsPreserved() {
        ReferenceSearch.search((query,callback)->fail("Invalid input must not request"),Collections.singletonMap("stationId","invalid"),
            (result,error,code)->{assertNull(result);assertEquals(R.string.reference_invalid,error);});
        ReferenceSearch.search((query,callback)->callback.onComplete(null,R.string.session_expired,401),
            Collections.singletonMap("reservationId",DisplayReference.reservation(ID)),
            (result,error,code)->{assertNull(result);assertEquals(401,code);});
    }
}
