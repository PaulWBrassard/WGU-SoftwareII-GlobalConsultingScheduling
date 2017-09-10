This file contains details on implementation decisions for 
GlobalConsultingScheduling.

Database Usernames and Passwords to be used for testing:
Name: test Password: test ; Name: other Password: other;

The mysql-connector-java-5.1.42-bin.jar used has been placed in src.
--------------------------------------------------------
Comments regarding requirements:
A. 
Location and language are determined before successful log-in.
Language chosen to support is Spanish. "Commented out" line in start method can change to spanish.

B.
Customer records with database support id, name, address from user perspective;
createDate, createBy, lastUpdateBy are managed automatically. Field active seems
to me like it is for database administration and therefore unmanaged here. Local
list of customers maintained as well for ease of display.

C.
Lambda expressions are used to handle the ActionEvents with buttons all throughout,
including to maintain appointments. Updated appointments relate them to customer
as appropriate in the database. Local list of appointments maintained as well for
ease of display.

D.
Same page is used for monthly and weekly calendar display. Left and right buttons
will "scroll" by month or week depending on view.

E.
Time Zones and Daylight saving time is managed automatically through conversion
to the local user's zone for any user facing information. Times are saved in the 
database in UTC.

F.
Exception controls are handled with try/catch, try with resources, and throws clauses.
Each prevention requested is implemented with pop up alerts.

G.
In the case of alert messages, they are prompted by button clicks to attempt to
submit info. The buttons implement lambda expressions to handle the event.

H.
Reminders are run on login and the alert is implemented as a pop up information Alert.

I.
The additional report of my choice is to show customer demographic info.
Being able to view in which cities our customers reside can be very relevant to sales and marketing.

J.
User activity is tracked in ActivityLog.txt in the GlobalConsultingScheduling folder.
The log is starting clean for ease of grading/testing.

Thank you for your time and effort.
-Paul Brassard 07/10/2017