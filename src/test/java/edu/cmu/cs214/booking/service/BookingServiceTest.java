package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void cancelBookingFreesTheSlot() {
        BookingService svc = newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        String id = ((BookingResult.Confirmed) first).booking().id();

        svc.cancelBooking(id);

        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void cancelUnknownBookingIdIsANoOp() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));

        svc.cancelBooking("does-not-exist");

        assertEquals(1, svc.listBookings(roomA).size());
    }

    @Test
    void waitlistedUserIsPromotedWhenBookingAheadIsCancelled() {
        BookingService svc = newService();
        BookingResult aliceResult = svc.book(roomA, alice, new TimeInterval(600, 660));
        String aliceBookingId = ((BookingResult.Confirmed) aliceResult).booking().id();
        assertInstanceOf(
            BookingResult.Waitlisted.class,
            svc.book(roomA, bob, new TimeInterval(630, 700)));

        svc.cancelBooking(aliceBookingId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user());
        assertEquals(new TimeInterval(630, 700), bookings.get(0).interval());
    }

    @Test
    void waiterStillConflictingWithARemainingBookingIsNotPromoted() {
        BookingService svc = newService();
        BookingResult aliceResult = svc.book(roomA, alice, new TimeInterval(600, 660));
        String aliceBookingId = ((BookingResult.Confirmed) aliceResult).booking().id();
        svc.book(roomA, bob, new TimeInterval(700, 800));
        // Carol's interval overlaps Alice (cancelled) but also Bob (stays), so no promotion.
        svc.book(roomA, carol, new TimeInterval(630, 720));

        svc.cancelBooking(aliceBookingId);

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals(bob, bookings.get(0).user());
    }

    @Test
    void isAvailableTrueWhenRoomIsFree() {
        BookingService svc = newService();
        assertTrue(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    @Test
    void isAvailableFalseWhenAnExistingBookingEnclosesTheQuery() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        // Query 630-650 sits entirely inside the existing 600-660 booking: a clash.
        // The buggy check only tests the existing booking's start (600), which is
        // not inside [630, 650), so it wrongly reports the room as available.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(630, 650)));
    }

    @Test
    void isAvailableFalseWhenTheQueryOverlapsTheEndOfAnExistingBooking() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        // Query 640-680 overlaps 640-660. Existing start 600 is not in [640, 680),
        // so the buggy check misses it.
        assertFalse(svc.isAvailable(roomA, new TimeInterval(640, 680)));
    }

    @Test
    void isAvailableTrueForABackToBackInterval() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        // Half-open intervals that merely touch do not overlap.
        assertTrue(svc.isAvailable(roomA, new TimeInterval(660, 720)));
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }
}
