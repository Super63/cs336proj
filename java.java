import java.sql.*;

public class UserService2 {

    private Connection conn;

    public UserService(Connection conn) {
        this.conn = conn;
    }

    // 1. Book Flight
    public boolean bookFlight(int customerId, int flightId, double totalFare, String seatNum, String ticketClass, double bookingFee) {
        try {
            conn.setAutoCommit(false);

            String updateSeats = "UPDATE Flight SET available_seats = available_seats - 1 WHERE flight_id = ? AND available_seats > 0";
            try (PreparedStatement ps1 = conn.prepareStatement(updateSeats)) {
                ps1.setInt(1, flightId);
                int rows = ps1.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            String insertTicket = "INSERT INTO Ticket (customer_id, total_fare, class, booking_fee, purchase_time, status) VALUES (?, ?, ?, ?, NOW(), 'active')";
            int ticketId;

            try (PreparedStatement ps2 = conn.prepareStatement(insertTicket, Statement.RETURN_GENERATED_KEYS)) {
                ps2.setInt(1, customerId);
                ps2.setDouble(2, totalFare);
                ps2.setString(3, ticketClass);
                ps2.setDouble(4, bookingFee);
                ps2.executeUpdate();

                try (ResultSet rs = ps2.getGeneratedKeys()) {
                    rs.next();
                    ticketId = rs.getInt(1);
                }
            }

            String linkFlight = "INSERT INTO Ticket_Flights (ticket_id, flight_id, seat_num, depart_time, arrival_time) " +
                    "SELECT ?, f.flight_id, ?, f.depart_time, f.arrival_time FROM Flight f WHERE f.flight_id = ?";

            try (PreparedStatement ps3 = conn.prepareStatement(linkFlight)) {
                ps3.setInt(1, ticketId);
                ps3.setString(2, seatNum);
                ps3.setInt(3, flightId);
                ps3.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            e.printStackTrace();
            return false;
        }
    }

    // 2. Add to Waiting List
    public void addToWaitingList(int customerId, int flightId) {
        String sql = "INSERT INTO WaitList (customer_id, flight_id, request_time) VALUES (?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, flightId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. View Past Reservations
    public ResultSet viewPastReservations(int customerId) {
        try {
            String sql = "SELECT t.ticket_id, t.class, t.total_fare, tf.flight_id, tf.depart_time " +
                         "FROM Ticket t JOIN Ticket_Flights tf ON t.ticket_id = tf.ticket_id " +
                         "WHERE t.customer_id = ? AND tf.depart_time < NOW() AND t.status = 'active'";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, customerId);
            return ps.executeQuery();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 4. View Upcoming Reservations
    public ResultSet viewUpcomingReservations(int customerId) {
        try {
            String sql = "SELECT t.ticket_id, t.class, t.total_fare, tf.flight_id, tf.depart_time " +
                         "FROM Ticket t JOIN Ticket_Flights tf ON t.ticket_id = tf.ticket_id " +
                         "WHERE t.customer_id = ? AND tf.depart_time >= NOW() AND t.status = 'active'";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, customerId);
            return ps.executeQuery();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 5. Cancel Reservation
    public boolean cancelReservation(int ticketId) {
        try {
            conn.setAutoCommit(false);

            String ticketClass;

            String checkClass = "SELECT class FROM Ticket WHERE ticket_id = ?";
            try (PreparedStatement ps1 = conn.prepareStatement(checkClass)) {
                ps1.setInt(1, ticketId);
                try (ResultSet rs = ps1.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    ticketClass = rs.getString("class");
                }
            }

            if (ticketClass.equalsIgnoreCase("economy")) {
                conn.rollback();
                return false;
            }

            String cancel = "UPDATE Ticket SET status = 'cancelled' WHERE ticket_id = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(cancel)) {
                ps2.setInt(1, ticketId);
                ps2.executeUpdate();
            }

            int flightId = -1;

            String restore = "SELECT flight_id FROM Ticket_Flights WHERE ticket_id = ?";
            try (PreparedStatement ps3 = conn.prepareStatement(restore)) {
                ps3.setInt(1, ticketId);
                try (ResultSet rs = ps3.executeQuery()) {
                    if (rs.next()) {
                        flightId = rs.getInt("flight_id");
                    }
                }
            }

            String updateSeats = "UPDATE Flight SET available_seats = available_seats + 1 WHERE flight_id = ?";
            try (PreparedStatement ps4 = conn.prepareStatement(updateSeats)) {
                ps4.setInt(1, flightId);
                ps4.executeUpdate();
            }

            notifyNextCustomer(flightId);
            conn.commit();
            return true;

        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            e.printStackTrace();
            return false;
        }
    }

    // 6. Notify Next Customer
    public void notifyNextCustomer(int flightId) {
        try {
            String query = "SELECT customer_id FROM WaitList WHERE flight_id = ? ORDER BY request_time ASC LIMIT 1";

            int customerId = -1;

            try (PreparedStatement ps1 = conn.prepareStatement(query)) {
                ps1.setInt(1, flightId);
                try (ResultSet rs = ps1.executeQuery()) {
                    if (rs.next()) {
                        customerId = rs.getInt("customer_id");
                    } else {
                        return;
                    }
                }
            }

            String notify = "INSERT INTO Notifications (customer_id, message) VALUES (?, ?)";
            try (PreparedStatement ps2 = conn.prepareStatement(notify)) {
                ps2.setInt(1, customerId);
                ps2.setString(2, "A seat is now available for your requested flight.");
                ps2.executeUpdate();
            }

            String delete = "DELETE FROM WaitList WHERE customer_id = ? AND flight_id = ? LIMIT 1";
            try (PreparedStatement ps3 = conn.prepareStatement(delete)) {
                ps3.setInt(1, customerId);
                ps3.setInt(2, flightId);
                ps3.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 7. Post Question
    public void postQuestion(int customerId, String question) {
        String sql = "INSERT INTO Questions (customer_id, question_text) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, question);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
