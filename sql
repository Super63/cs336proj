/*
[1] make flight reservations/buy the ticket.
[2] enter the waiting list if the flight is full
[3] view all the past flight reservations with their details
[4] view all the upcoming flights with their details
[5] cancel their flight reservations (if it is business or first class)
[6] send an alert to customers in the waiting list that there is an empty seat
[7] post questions to the customer representative
*/

-- (assuming Customer, Flight exist, if the referred keys are different, update them)

-- Creating Ticket
CREATE TABLE Ticket (
    ticket_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    total_fare DECIMAL(10,2),
    class ENUM('economy', 'business', 'first'),
    booking_fee DECIMAL(10,2),
    purchase_time DATETIME,
    status ENUM ('active', 'cancelled') DEFAULT 'active',
    FOREIGN KEY (customer_id) REFERENCES Customer(customer_id)
);

-- Creating Ticket_Flights
CREATE TABLE Ticket_Flights (
    ticket_id INT,
    flight_id INT,
    seat_num VARCHAR(10),
    depart_time DATETIME,
    arrival_time DATETIME,
    PRIMARY KEY (ticket_id, flight_id),
    FOREIGN KEY (ticket_id) REFERENCES Ticket(ticket_id),
    FOREIGN KEY (flight_id) REFERENCES Flight(flight_id)
);

-- Creating WaitList
CREATE TABLE WaitList (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    flight_id INT,
    request_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES Customer(customer_id),
    FOREIGN KEY (flight_id) REFERENCES Flight(flight_id)
);

-- Creating Notifications
CREATE TABLE Notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES Customer(customer_id)
);

-- Creating Questions
CREATE TABLE Questions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    question_text TEXT,
    answer_text TEXT,
    status ENUM('open', 'answered') DEFAULT 'open',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES Customer(customer_id)
);





--1 - make flight reservations/buy the ticket.
--Check if flight has avalible seats
SELECT avalible_seats
FROM flight
WHERE flight_id = ?;

--IF avalible_seats > 0:
--Insert ticket
INSERT INTO Ticket (customer_id, total_fare, class, booking_fee, purchase_time)
VALUES (?, ?, ?, ?, NOW());

--Link to flight
INSERT INTO Ticket_Flights (ticket_id, flight_id, seat_num, depart_time, arrival_time)
VALUES (?, ?, ?, ?, ?);

--Lower Avalible Seats
UPDATE Flight
SET avalible_seats = avalible_seats - 1
WHERE flight_id = ? AND avalible_seats > 0;

--2 - enter the waiting list if the flight is full
--IF avalible_seats = 0:
--Add to WaitList
INSERT INTO WaitList (customer_id, flight_id)
VALUES(?, ?);

--3 - view all the past flight reservations with their details
SELECT t.ticket_id, t.class, t.total_fare, tf.flight_id, tf.depart_time
FROM Ticket t
JOIN Ticket_Flights tf ON t.ticket_id = tf.ticket_id
WHERE t.customer_id = ?
AND tf.depart_time < NOW()
AND t.status = 'active';

--4 - view all the upcoming flights with their details
SELECT t.ticket_id, t.class, t.total_fare, tf.flight_id, tf.depart_time
FROM Ticket t
JOIN Ticket_Flights tf ON t.ticket_id = tf.ticket_id
WHERE t.customer_id = ?
AND tf.depart_time >= NOW()
AND t.status = 'active';

--5 - cancel their flight reservations (if it is business or first class)
--IF NOT 'economy':
--Find the ticket
SELECT class
FROM Ticket
WHERE ticket_id = ?

--Cancel the ticket
UPDATE Ticket
SET status = 'cancelled'
WHERE ticket_id = ?;

--Update Seat Counts
UPDATE Flight f
JOIN Ticket_Flights tf ON f.flight_id = tf.flight_id
SET f.avalible_seats = f.avalible_seats + 1
WHERE tf.ticket_id = ?;

--6 - send an alert to customers in the waiting list that there is an empty seat
--find first person to notify
SELECT customer_id
FROM WaitList
WHERE flight_id = ?
ORDER BY request_time ASC
LIMIT 1;

--send noti
INSERT INTO Notifications (customer_id, message)
VALUES(?, 'A seat is now avalible for your requested flight.')

--update WaitList
DELETE FROM WaitList
WHERE customer_id = ?
AND flight_id = ?
LIMIT 1;

--7 - post questions to the customer representative
INSERT INTO Questions (customer_id, question_text)
VALUES(?, ?);
