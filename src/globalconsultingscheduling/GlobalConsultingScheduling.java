package globalconsultingscheduling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 *
 * @author Paul Brassard
 */
public class GlobalConsultingScheduling extends Application {
    
    //Enum Option is to advise whether the option chosen was to add or edit.
    private enum Option {
        ADD, EDIT;
    }
    
    private GridPane login;
    private ResourceBundle rb;
    private Alert errorAlert, reminderAlert, confirmAlert;
    private StackPane mainPane;
    private Stage primaryStage;
    private Connection conn;
    private String user, selectedView;
    private ZoneId myZone, utc;
    private LocalDateTime utcNow;
    private Locale locale;
    private ObservableList<Customer> customers;
    private ObservableList<Appointment> appointments;
    private int selectedCustomerId, selectedAppointmentId, selectedYear;
    private Month selectedMonth;
    private BufferedWriter log;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        //Initializing defaults
        this.primaryStage = primaryStage;
        try {
            log = new BufferedWriter(new FileWriter("ActivityLog.txt", true));
        } catch (IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        locale = Locale.getDefault();
        //locale = new Locale.Builder().setLanguage("es").build(); //Uncomment for Spanish
        rb = ResourceBundle.getBundle("GCS", locale);
        myZone = ZoneId.systemDefault();
        utc = ZoneId.of("UTC");
        utcNow = null;
        conn = null;
        selectedMonth = Month.JANUARY;
        selectedYear = 2017;
        selectedView = "month";
        errorAlert = new Alert(AlertType.ERROR);
        confirmAlert = new Alert(AlertType.CONFIRMATION);
        confirmAlert.setHeaderText(rb.getString("youSure"));
        confirmAlert.getButtonTypes().clear(); //I don't want ok and cancel
        confirmAlert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
        mainPane = new StackPane();
        
        connectDB();
        launchLoginPage();
        Scene scene = new Scene(mainPane, 300, 250);
        scene.getStylesheets().add(GlobalConsultingScheduling.class.getResource("darkTheme.css").toExternalForm());
        primaryStage.setTitle(rb.getString("title"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    //Connect to the database
    private void connectDB() {
        String driver = "com.mysql.jdbc.Driver";
        String db = "U04ASC";
        String url = "jdbc:mysql://52.206.157.109/" + db;
        String username = "U04ASC";
        String password = "53688183694";
        try {
            Class.forName(driver);
            conn = DriverManager.getConnection(url, username, password);
            log.write("DB connected");
            log.newLine();
            log.flush();
        } catch (SQLException ex) {
            //If there is an issue connecting to db, display an alert
            errorAlert = new Alert(AlertType.INFORMATION);
            errorAlert.setHeaderText(rb.getString("connectionError"));
            errorAlert.setContentText(String.format("SQLException: %s\nSQLState: %s\nVendorError: %s",
                    ex.getMessage(), ex.getSQLState(), ex.getErrorCode()));
            errorAlert.showAndWait();
        } catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Create a page where an account can be logged into
    private void launchLoginPage() {
        mainPane.getChildren().clear();
        login = new GridPane();
        login.setAlignment(Pos.CENTER);
        login.setHgap(10);
        login.setVgap(10);
        login.setPadding(new Insets(25, 0, 25, 0));
        Label titleLabel = new Label(rb.getString("title"));
        Label nameLabel = new Label(rb.getString("usernameLabel"));
        Label passwordLabel = new Label(rb.getString("passwordLabel"));
        TextField nameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button(rb.getString("login"));
        loginButton.setOnAction((ActionEvent e) -> {
            //Send username and password for login attempt
            attemptLogin(nameField.getText(), passwordField.getText());
        });
        Button exit = new Button(rb.getString("exit"));
        exit.setOnAction((ActionEvent e) -> {
            Platform.exit();
        });
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(loginButton, exit);
        login.add(titleLabel, 0, 0, 4, 1);
        login.add(nameLabel, 0, 2);
        login.add(nameField, 1, 2);
        login.add(passwordLabel, 0, 3);
        login.add(passwordField, 1, 3);
        login.add(buttonBox, 0, 4, 4, 1);
        mainPane.getChildren().add(login);
        primaryStage.setHeight(250);
        primaryStage.setWidth(300);
    }

    //Match username and password to database records for successfull login
    private void attemptLogin(String username, String password) {
        String selectUser = String.format("SELECT * FROM user WHERE userName='%s' and password='%s';", username, password);
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(selectUser)) {
            //If we get a match then username and password match; Write to log
            if (rs.last()) {
                user = username;
                utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
                log.write(String.format("Login successful as %s\nat %s\n%s", user, utcNow, locale));
                log.newLine();
                log.flush();
                runReminder();
                launchHomePage();
            } else {
                errorAlert = new Alert(AlertType.INFORMATION);
                errorAlert.setHeaderText(rb.getString("invalidLogin"));
                errorAlert.showAndWait();
            }
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Compare current time with appointment start times in the database to show alerts 15 minutes in advance.
    private void runReminder() {
        String select = "SELECT * FROM appointment;";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(select)) {
            String message = null;
            while (rs.next()) {
                LocalDateTime start = ((Timestamp) rs.getObject("start")).toLocalDateTime();
                Duration difference = Duration.between(utcNow, start);
                //Convert start LocalDateTime from UTC to be displayed in local time zone
                ZonedDateTime zonedStart = ZonedDateTime.ofInstant(ZonedDateTime.of(start, utc).toInstant(), myZone);

                //Create the reminder message if it starts within 900 seconds (15 min)
                if (difference.getSeconds() < 900 && !difference.isNegative()) {
                    message = String.format("%s with customer %s at %s local time,\n%s UTC",
                            rs.getString("title"), rs.getInt("customerId"), zonedStart.toLocalTime(), start.toLocalTime());
                }
            }
            if (message != null) {
                reminderAlert = new Alert(AlertType.INFORMATION);
                reminderAlert.setHeaderText(rb.getString("appointmentSoon"));
                reminderAlert.setContentText(message);
                reminderAlert.showAndWait();
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Create the "Home Page" from which all activities will be accessible
    private void launchHomePage() {
        mainPane.getChildren().clear();
        VBox homePage = new VBox(10);
        
        Button reports = new Button(rb.getString("reports"));
        reports.setOnAction((ActionEvent e) -> {
            launchReportsPage();
        });
        Button calendar = new Button(rb.getString("calendar"));
        calendar.setOnAction((ActionEvent e) -> {
            launchCalendar(selectedMonth, selectedYear);
        });
        Button exit = new Button(rb.getString("logout"));
        exit.setOnAction((ActionEvent e) -> {
            utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
            //Logging out from user; Write to log.
            try {
                log.write(String.format("Logged out as %s\nat %s\n", user, utcNow));
                log.newLine();
                log.newLine();
                log.flush();
            } catch (IOException ex) {
                Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
            }
            user = null;
            launchLoginPage();
        });
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(Pos.BOTTOM_LEFT);
        optionsBox.getChildren().addAll(reports, calendar);
        HBox bottom = new HBox(170);
        bottom.setAlignment(Pos.BOTTOM_RIGHT);
        bottom.setPadding(new Insets(10));
        bottom.getChildren().addAll(optionsBox, exit);
        
        HBox splitter = new HBox(10);
        splitter.setPadding(new Insets(10));
        //Left side of HomePage
        VBox leftSide = new VBox(10);
        customers = FXCollections.observableArrayList();
        loadCustomers();
        TableView custTable = new TableView(customers);
        TableColumn nameColumn = new TableColumn(rb.getString("customerName"));
        nameColumn.setMinWidth(150);
        nameColumn.setResizable(false);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        TableColumn idColumn = new TableColumn(rb.getString("customerId"));
        idColumn.setMinWidth(150);
        idColumn.setResizable(false);
        idColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        custTable.setItems(customers);
        custTable.getColumns().addAll(nameColumn, idColumn);
        custTable.getSortOrder().add(nameColumn);
        HBox buttons = new HBox(20);
        buttons.setAlignment(Pos.CENTER);
        Button newCust = new Button(rb.getString("newCustomer"));
        newCust.setOnAction((ActionEvent e) -> {
            launchCustomerPage(Option.ADD);
        });
        Button editCust = new Button(rb.getString("editCustomer"));
        editCust.setOnAction((ActionEvent e) -> {
            Customer c = (Customer) custTable.getSelectionModel().getSelectedItem();
            if (c != null) {
                selectedCustomerId = c.getCustomerId();
                launchCustomerPage(Option.EDIT);
            }
        });
        buttons.getChildren().addAll(newCust, editCust);
        leftSide.getChildren().addAll(custTable, buttons);
        //Right side of HomePage
        VBox rightSide = new VBox(10);
        appointments = FXCollections.observableArrayList();
        loadAppointments();
        TableView appointmentTable = new TableView(appointments);
        TableColumn titleColumn = new TableColumn(rb.getString("appointmentTitle"));
        titleColumn.setMinWidth(150);
        titleColumn.setResizable(false);
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        TableColumn appIdColumn = new TableColumn(rb.getString("customerId"));
        appIdColumn.setMinWidth(150);
        appIdColumn.setResizable(false);
        appIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        appointmentTable.setItems(appointments);
        appointmentTable.getColumns().addAll(titleColumn, appIdColumn);
        appointmentTable.getSortOrder().add(titleColumn);
        Button newAppointment = new Button(rb.getString("newAppointment"));
        newAppointment.setOnAction((ActionEvent e) -> {
            Customer c = (Customer) custTable.getSelectionModel().getSelectedItem();
            //If there is a customer selected save Id to populate appointment, else -1 ensures no cust info accidentally used 
            selectedCustomerId = c != null ? c.getCustomerId() : -1;
            launchAppointmentPage(Option.ADD);
        });
        Button editAppointment = new Button(rb.getString("editAppointment"));
        editAppointment.setOnAction((ActionEvent e) -> {
            Appointment a = (Appointment) appointmentTable.getSelectionModel().getSelectedItem();
            if (a != null) {
                selectedAppointmentId = a.getAppointmentId();
                launchAppointmentPage(Option.EDIT);
            }
        });
        HBox appointmentBox = new HBox(20);
        appointmentBox.setAlignment(Pos.CENTER);
        appointmentBox.getChildren().addAll(newAppointment, editAppointment);
        rightSide.getChildren().addAll(appointmentTable, appointmentBox);
        splitter.getChildren().addAll(leftSide, rightSide);
        homePage.getChildren().addAll(splitter, bottom);
        mainPane.getChildren().add(homePage);
        primaryStage.setHeight(400);
        primaryStage.setWidth(650);
    }

    //Create the "Customer Page" from which customers can be managed.
    private void launchCustomerPage(Option option) {
        mainPane.getChildren().clear();
        GridPane addCustPane = new GridPane();
        addCustPane.setAlignment(Pos.CENTER);
        addCustPane.setHgap(10);
        addCustPane.setVgap(10);
        addCustPane.setPadding(new Insets(25, 0, 25, 0));
        Label addCustLabel = new Label(rb.getString("addCustLabel"));
        Label nameLabel = new Label(rb.getString("nameLabel"));
        Label addressLabel = new Label(rb.getString("addressLabel"));
        Label address2Label = new Label(rb.getString("address2Label"));
        Label phoneLabel = new Label(rb.getString("phoneLabel"));
        Label postalLabel = new Label(rb.getString("postalLabel"));
        Label cityLabel = new Label(rb.getString("cityLabel"));
        Label countryLabel = new Label(rb.getString("countryLabel"));
        TextField nameTF = new TextField();
        TextField phoneTF = new TextField();
        TextField addressTF = new TextField();
        TextField address2TF = new TextField();
        TextField cityTF = new TextField();
        TextField postalTF = new TextField();
        TextField countryTF = new TextField();
        //If we are editing a customer, pull customer by Id from customer List and fill in TextFields.
        if (option.equals(Option.EDIT)) {
            Optional optional = customers.stream().filter(c -> c.getCustomerId() == selectedCustomerId).findAny();
            if (optional.isPresent()) {
                Customer selected = (Customer) optional.get();
                loadAddressInfo(selected);
                nameTF.setText(selected.getCustomerName());
                phoneTF.setText(selected.getPhone());
                addressTF.setText(selected.getAddress());
                address2TF.setText(selected.getAddress2());
                cityTF.setText(selected.getCity());
                postalTF.setText(selected.getPostalCode());
                countryTF.setText(selected.getCountry());
            }
        }
        Button enter = new Button(rb.getString("enter"));
        enter.setOnAction((ActionEvent e) -> {
            //Add does insert into db, edit does update
            if (option.equals(Option.ADD)) {
                insertCustomer(nameTF.getText(), phoneTF.getText(), addressTF.getText(), address2TF.getText(), cityTF.getText(), postalTF.getText(), countryTF.getText());
            }
            if (option.equals(Option.EDIT)) {
                updateCustomer(selectedCustomerId, nameTF.getText(), phoneTF.getText(), addressTF.getText(), address2TF.getText(), cityTF.getText(), postalTF.getText(), countryTF.getText());
            }
            launchHomePage();
        });
        Button cancel = new Button(rb.getString("cancel"));
        cancel.setOnAction((ActionEvent e) -> {
            launchHomePage();
        });
        Button delete = new Button(rb.getString("delete"));
        delete.setOnAction((ActionEvent e) -> {
            //Delete while editing deletes from db, while adding we can just discard info and return to home.
            if (option.equals(Option.EDIT)) {
                confirmAlert.setContentText(rb.getString("customerDelete"));
                Optional response = confirmAlert.showAndWait();
                if (response.get().equals(ButtonType.YES)) {
                    deleteCustomer(selectedCustomerId);
                } else {
                    return;
                }
            }
            launchHomePage();
        });
        HBox buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.getChildren().addAll(enter, cancel, delete);
        
        addCustPane.add(addCustLabel, 0, 0, 4, 1);
        addCustPane.add(nameLabel, 0, 2);
        addCustPane.add(nameTF, 1, 2);
        addCustPane.add(phoneLabel, 0, 3);
        addCustPane.add(phoneTF, 1, 3);
        addCustPane.add(addressLabel, 0, 4);
        addCustPane.add(addressTF, 1, 4);
        addCustPane.add(cityLabel, 0, 5);
        addCustPane.add(cityTF, 1, 5);
        addCustPane.add(postalLabel, 0, 6);
        addCustPane.add(postalTF, 1, 6);
        addCustPane.add(countryLabel, 0, 7);
        addCustPane.add(countryTF, 1, 7);
        addCustPane.add(address2Label, 0, 8);
        addCustPane.add(address2TF, 1, 8);
        addCustPane.add(buttonsBox, 0, 9, 4, 1);
        mainPane.getChildren().add(addCustPane);
        primaryStage.setWidth(400);
        primaryStage.setHeight(400);
    }

    //Create the "Appointment Page" from which appointments can be managed.
    private void launchAppointmentPage(Option option) {
        //Business hours set from 9AM-5PM
        LocalTime startOfBusiness = LocalTime.of(9, 0);
        LocalTime endOfBusiness = LocalTime.of(17, 0);
        
        mainPane.getChildren().clear();
        GridPane schedulerPane = new GridPane();
        schedulerPane.setAlignment(Pos.CENTER);
        schedulerPane.setHgap(10);
        schedulerPane.setVgap(10);
        schedulerPane.setPadding(new Insets(25, 0, 25, 0));
        Label scheduleLabel = new Label(rb.getString("scheduleLabel"));
        Label customerLabel = new Label(rb.getString("customerLabel"));
        Label titleLabel = new Label(rb.getString("titleLabel"));
        Label descriptionLabel = new Label(rb.getString("descriptionLabel"));
        Label locationLabel = new Label(rb.getString("locationLabel"));
        Label contactLabel = new Label(rb.getString("contactLabel"));
        Label urlLabel = new Label(rb.getString("urlLabel"));
        Label startLabel = new Label(rb.getString("startLabel"));
        Label endLabel = new Label(rb.getString("endLabel"));
        TextField customerTF = new TextField();
        TextField titleTF = new TextField();
        TextField descriptionTF = new TextField();
        TextField locationTF = new TextField();
        TextField contactTF = new TextField();
        TextField urlTF = new TextField();
        DatePicker startDP = new DatePicker();
        ObservableList<Integer> hours = FXCollections.observableArrayList((Stream.iterate(1, n -> n + 1)).limit(12).toArray(Integer[]::new));
        ObservableList<String> minutes = FXCollections.observableArrayList("00", "10", "20", "30", "40", "50");
        ObservableList<String> meridiem = FXCollections.observableArrayList("AM", "PM");
        ComboBox startHour = new ComboBox(hours);
        startHour.getSelectionModel().selectFirst();
        ComboBox startMinute = new ComboBox(minutes);
        startMinute.getSelectionModel().selectFirst();
        ComboBox startAMPM = new ComboBox(meridiem);
        startAMPM.getSelectionModel().selectFirst();
        DatePicker endDP = new DatePicker();
        ComboBox endHour = new ComboBox(hours);
        endHour.getSelectionModel().selectFirst();
        ComboBox endMinute = new ComboBox(minutes);
        endMinute.getSelectionModel().selectFirst();
        ComboBox endAMPM = new ComboBox(meridiem);
        endAMPM.getSelectionModel().selectFirst();
        //If we are adding appointment and customer was selected, populate form with customer info.
        if (option.equals(Option.ADD) && selectedCustomerId >= 0) {
            Optional custOptional = customers.stream().filter(c -> c.getCustomerId() == selectedCustomerId).findAny();
            if (custOptional.isPresent()) {
                Customer c = (Customer) custOptional.get();
                customerTF.setText("" + c.getCustomerId());
            }
        }
        //If we are editing an appointment, load the info into the TextFields appropriately.
        if (option.equals(Option.EDIT)) {
            Optional optional = appointments.stream().filter(a -> a.getAppointmentId() == selectedAppointmentId).findAny();
            if (optional.isPresent()) {
                Appointment selected = (Appointment) optional.get();
                customerTF.setText("" + selected.getCustomerId());
                titleTF.setText(selected.getTitle());
                descriptionTF.setText(selected.getDescription());
                locationTF.setText(selected.getLocation());
                contactTF.setText(selected.getContact());
                urlTF.setText(selected.getUrl());
                LocalDateTime startLDT = selected.getStart();
                startDP.setValue(startLDT.toLocalDate());
                LocalDateTime endLDT = selected.getEnd();
                endDP.setValue(endLDT.toLocalDate());
                //Set start hour, minute, meridiem
                LocalTime sTime = startLDT.toLocalTime();
                int sHour = sTime.getHour();
                if (sHour > 12) {
                    startAMPM.getSelectionModel().select(1);
                    sHour -= 12;
                } else {
                    startAMPM.getSelectionModel().select(0);
                }
                startHour.getSelectionModel().select(sHour - 1);
                startMinute.getSelectionModel().select((sTime.getMinute() / 10));
                //Set end hour, minute, meridiem
                LocalTime eTime = endLDT.toLocalTime();
                int eHour = eTime.getHour();
                if (eHour > 12) {
                    endAMPM.getSelectionModel().select(1);
                    eHour -= 12;
                } else {
                    endAMPM.getSelectionModel().select(0);
                }
                endHour.getSelectionModel().select(eHour - 1);
                endMinute.getSelectionModel().select((eTime.getMinute() / 10));
            }
        }
        Button schedule = new Button(rb.getString("schedule"));
        schedule.setOnAction((ActionEvent e) -> {
            //If PM selected add 12 making the hour into military time
            Integer sHourBefore = (Integer) startHour.getSelectionModel().getSelectedItem();
            Integer sHour = startAMPM.getSelectionModel().getSelectedItem().equals("AM") ? sHourBefore : sHourBefore + 12;
            Integer sMinute = Integer.parseInt((String) startMinute.getSelectionModel().getSelectedItem());
            LocalTime sTime = LocalTime.of(sHour, sMinute);
            Integer eHourBefore = (Integer) endHour.getSelectionModel().getSelectedItem();
            Integer eHour = endAMPM.getSelectionModel().getSelectedItem().equals("AM") ? eHourBefore : eHourBefore + 12;
            Integer eMinute = Integer.parseInt((String) endMinute.getSelectionModel().getSelectedItem());
            LocalTime eTime = LocalTime.of(eHour, eMinute);
            LocalDateTime start, end;
            //try to get the date, if we cannot parse, prompt user for correction.
            try {
                start = LocalDateTime.of(startDP.getValue(), sTime);
                end = LocalDateTime.of(endDP.getValue(), eTime);
            } catch (NullPointerException ex) {
                errorAlert.setHeaderText(rb.getString("invalidDate"));
                errorAlert.setContentText(rb.getString("selectDates"));
                errorAlert.showAndWait();
                return;
            }
            //If the appointment ends before it starts, or is outside of business hours, alert to try again.
            if (end.compareTo(start) < 0 || start.toLocalTime().isBefore(startOfBusiness) || end.toLocalTime().isAfter(endOfBusiness)) {
                errorAlert.setHeaderText(rb.getString("invalidTime"));
                errorAlert.setContentText(rb.getString("withinBizHours"));
                errorAlert.showAndWait();
            } else {
                //try to insert or update appointment but force retry with overlap, invalid customer data, and invalid times.
                try {
                    if (option.equals(Option.ADD)) {
                        insertAppointment(Integer.parseInt(customerTF.getText()), titleTF.getText(), descriptionTF.getText(),
                                locationTF.getText(), contactTF.getText(), urlTF.getText(), start, end);
                    }
                    if (option.equals(Option.EDIT)) {
                        updateAppointment(selectedAppointmentId, Integer.parseInt(customerTF.getText()), titleTF.getText(), descriptionTF.getText(),
                                locationTF.getText(), contactTF.getText(), urlTF.getText(), start, end);
                    }
                    launchHomePage();
                } catch (NumberFormatException ex) {
                    errorAlert.setHeaderText(rb.getString("validCustomer"));
                    errorAlert.setContentText(rb.getString("idIsNumber"));
                    errorAlert.showAndWait();
                    //deal with invalid customer data or overlapping times
                } catch (IllegalArgumentException ex) {
                    errorAlert.setHeaderText(rb.getString("appointmentIssue"));
                    errorAlert.setContentText(ex.getMessage());
                    errorAlert.showAndWait();
                }
            }
        });
        Button cancel = new Button(rb.getString("cancel"));
        cancel.setOnAction((ActionEvent e) -> {
            launchHomePage();
        });
        Button delete = new Button(rb.getString("delete"));
        delete.setOnAction((ActionEvent e) -> {
            //If we are editing, delete from database; For adding we just return to home.
            if (option.equals(Option.EDIT)) {
                confirmAlert.setContentText(rb.getString("appointmentDelete"));
                Optional response = confirmAlert.showAndWait();
                if (response.get().equals(ButtonType.YES)) {
                    deleteAppointment(selectedAppointmentId);
                } else {
                    return;
                }
            }
            launchHomePage();
        });
        HBox startTime = new HBox();
        startTime.getChildren().addAll(startHour, startMinute, startAMPM);
        HBox endTime = new HBox();
        endTime.getChildren().addAll(endHour, endMinute, endAMPM);
        HBox buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.getChildren().addAll(schedule, cancel, delete);
        
        schedulerPane.add(scheduleLabel, 0, 0, 4, 1);
        schedulerPane.add(customerLabel, 0, 1);
        schedulerPane.add(customerTF, 1, 1);
        schedulerPane.add(titleLabel, 0, 2);
        schedulerPane.add(titleTF, 1, 2);
        schedulerPane.add(descriptionLabel, 0, 3);
        schedulerPane.add(descriptionTF, 1, 3);
        schedulerPane.add(locationLabel, 0, 4);
        schedulerPane.add(locationTF, 1, 4);
        schedulerPane.add(contactLabel, 0, 5);
        schedulerPane.add(contactTF, 1, 5);
        schedulerPane.add(urlLabel, 0, 6);
        schedulerPane.add(urlTF, 1, 6);
        schedulerPane.add(startLabel, 0, 7);
        schedulerPane.add(startDP, 1, 7);
        schedulerPane.add(startTime, 2, 7);
        schedulerPane.add(endLabel, 0, 8);
        schedulerPane.add(endDP, 1, 8);
        schedulerPane.add(endTime, 2, 8);
        schedulerPane.add(buttonsBox, 0, 9, 4, 1);
        
        mainPane.getChildren().add(schedulerPane);
        primaryStage.setWidth(500);
        primaryStage.setHeight(400);
    }

    //Create the "Calendar" from which appointments can be viewed by month or week.
    private void launchCalendar(Month month, int year) {
        mainPane.getChildren().clear();
        VBox calPage = new VBox(10);
        AppointmentCalendar c = new AppointmentCalendar();
        c.setMonth(month);
        c.setCurrentYear(year);
        //Ensure selectedView persists through screen changes
        if (selectedView.equals("week")) {
            c.viewAsWeek();
        }
        ObservableList<Month> months = FXCollections.observableArrayList(Month.values());
        ComboBox monthBox = new ComboBox(months);
        monthBox.getSelectionModel().select(selectedMonth);
        monthBox.valueProperty().addListener((ObservableValue observable, Object oldValue, Object newValue) -> {
            selectedMonth = (Month) newValue;
            c.setMonth(selectedMonth);
            launchCalendar(selectedMonth, selectedYear);
        });
        ObservableList<Integer> years = FXCollections.observableArrayList(2017, 2018, 2019);
        ComboBox yearBox = new ComboBox(years);
        yearBox.getSelectionModel().select(Integer.valueOf(selectedYear));
        yearBox.valueProperty().addListener((ObservableValue observable, Object oldValue, Object newValue) -> {
            selectedYear = Integer.parseInt(((Integer) newValue).toString());
            c.setCurrentYear(selectedYear);
            launchCalendar(selectedMonth, selectedYear);
        });
        Button left = new Button("<");
        left.setOnAction((ActionEvent e) -> {
            //Scroll weeks or months depending on view
            if (selectedView.equals("week")) {
                if (c.previousWeek()) {
                    displayAppointmentsOnCalendar(month, year, c);
                }
            } else {
                monthBox.getSelectionModel().selectPrevious();
            }
        });
        Button right = new Button(">");
        right.setOnAction((ActionEvent e) -> {
            //Scroll weeks or months depending on view
            if (selectedView.equals("week")) {
                if (c.nextWeek()) {
                    displayAppointmentsOnCalendar(month, year, c);
                }
            } else {
                monthBox.getSelectionModel().selectNext();
            }
        });
        HBox arrows = new HBox();
        arrows.getChildren().addAll(left, right);
        HBox selectors = new HBox(175);
        selectors.getChildren().addAll(yearBox, monthBox, arrows);
        HBox buttons = new HBox(20);
        buttons.setAlignment(Pos.CENTER);
        Button home = new Button(rb.getString("home"));
        home.setOnAction((ActionEvent e) -> {
            launchHomePage();
        });
        Button view = new Button(selectedView.equals("month") ? rb.getString("weekly") : rb.getString("monthly"));
        view.setOnAction((ActionEvent e) -> {
            //If current view is by month, switching view goes to week; Change button to show ability to return to month view.
            if (selectedView.equals("month")) {
                selectedView = "week";
                view.setText(rb.getString("monthly"));
                c.viewAsWeek();
                primaryStage.setHeight(300);
                displayAppointmentsOnCalendar(month, year, c);
            } else {
                selectedView = "month";
                view.setText(rb.getString("weekly"));
                c.viewAsMonth();
                primaryStage.setHeight(650);
                displayAppointmentsOnCalendar(month, year, c);
            }
        });
        buttons.getChildren().addAll(view, home);
        displayAppointmentsOnCalendar(month, year, c);
        calPage.getChildren().addAll(selectors, c.getAsGridPane(), buttons);
        mainPane.getChildren().add(calPage);
        int size = selectedView.equals("month") ? 650 : 300;
        primaryStage.setWidth(650);
        primaryStage.setHeight(size);
    }

    //Create the "Reports Page" from which various reports and be created.
    private void launchReportsPage() {
        mainPane.getChildren().clear();
        VBox reports = new VBox(20);
        reports.setPadding(new Insets(10));
        HBox midSection = new HBox(20);
        midSection.setMinHeight(300);
        VBox left = new VBox(20);
        left.setMinWidth(200);
        VBox right = new VBox(20);
        ScrollPane rightScroll = new ScrollPane();
        rightScroll.setPadding(new Insets(10));
        Label displayTitle = new Label(rb.getString("reports"));
        displayTitle.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 15));
        displayTitle.setAlignment(Pos.CENTER);
        HBox titleBox = new HBox();
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().add(displayTitle);
        GridPane displayGrid = new GridPane();
        displayGrid.setVgap(10);
        displayGrid.setHgap(10);
        displayGrid.setAlignment(Pos.CENTER);
        Button typesByMonth = new Button(rb.getString("typesByMonth"));
        typesByMonth.setOnAction((ActionEvent e) -> {
            //Get the labels that show types by month.
            displayGrid.getChildren().clear();
            displayTitle.setText(rb.getString("typesByMonth"));
            Label[] labels = labelsOfAppointmentTypesByMonth();
            //Add to the grid in columns of 4
            int row = 0, column = 0;
            for (int i = 0; i < 12; i++) {
                displayGrid.add(labels[i], column, row);
                column++;
                if (column % 4 == 0) {
                    column = 0;
                    row++;
                }
            }
        });
        Button schedules = new Button(rb.getString("schedules"));
        schedules.setOnAction((ActionEvent e) -> {
            //Create a set of users based on existing appointments
            displayGrid.getChildren().clear();
            displayTitle.setText(rb.getString("schedules"));
            Set<String> users = new HashSet();
            appointments.forEach(a -> users.add(a.getCreatedBy()));
            //For each user make a label for their name and then all their appointments, display those.
            int row = -1, column;
            for (String currentUser : users) {
                column = 0;
                row++;
                Label label = new Label(currentUser + ":");
                displayGrid.add(label, column, row);
                row++;
                for (Appointment a : appointments) {
                    if (a.getCreatedBy().equals(currentUser)) {
                        displayGrid.add(new Label(a.toString()), column, row);
                        column++;
                    }
                }
            }
        });
        Button demographics = new Button(rb.getString("demographics"));
        demographics.setOnAction((ActionEvent e) -> {
            //Get the labels that display demographics
            displayGrid.getChildren().clear();
            displayTitle.setText(rb.getString("demographics"));
            ArrayList<Label> labelList = labelsOfDemographics();
            //Add to the grid in columns of 4
            int row = 0, column = 0;
            for (int i = 0; i < labelList.size(); i++) {
                displayGrid.add(labelList.get(i), column, row);
                column++;
                if (column % 4 == 0) {
                    column = 0;
                    row++;
                }
            }
        });
        Button home = new Button(rb.getString("home"));
        home.setOnAction((ActionEvent e) -> {
            launchHomePage();
        });
        HBox nav = new HBox();
        nav.getChildren().add(home);
        
        left.getChildren().addAll(typesByMonth, schedules, demographics);
        right.getChildren().addAll(titleBox, displayGrid);
        rightScroll.setContent(right);
        midSection.getChildren().addAll(left, rightScroll);
        reports.getChildren().addAll(midSection, nav);
        mainPane.getChildren().add(reports);
        primaryStage.setWidth(600);
        primaryStage.setHeight(400);
    }

    //Get a label[] that contains the month names along with how many appointment types each.
    private Label[] labelsOfAppointmentTypesByMonth() {
        Label[] labels = new Label[12];
        String select = "SELECT * FROM appointment;";
        try (Statement statement = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet rs = statement.executeQuery(select)) {
            Set<String> types = new HashSet();
            //Find appointments for each month
            for (int i = 0; i < 12; i++) {
                while (rs.next()) {
                    Month m = ((Timestamp) rs.getObject("start")).toLocalDateTime().getMonth();
                    int mValOffset = m.getValue() - 1;
                    //If the appointment is in the month we are currently dealing with add it's title to the set
                    if (mValOffset == i) {
                        String title = rs.getString("title");
                        types.add(title);
                    }
                }
                rs.beforeFirst(); //position the pointer for more checking
                //Count the set and add it to its corresponding Label
                String currentMonth = Month.of(i + 1).toString();
                int count = (int) types.stream().count();
                labels[i] = new Label(String.format("%s:\n%s types", currentMonth, count));
                types.clear();
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        return labels;
    }

    //Get an ArrayList of labels that describe the amount of customers in which cities.
    private ArrayList<Label> labelsOfDemographics() {
        ArrayList<Label> labels = new ArrayList<>();
        String select = "SELECT COUNT(customerName) as count, city.city FROM customer "
                + "LEFT JOIN address ON customer.addressId = address.addressId "
                + "LEFT JOIN city ON address.cityId = city.cityId group by city;";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(select)) {
            //Get each city and its corresponding count and add those labels to ArrayList.
            while (rs.next()) {
                String cityCount = String.format("City: %s\nCount: %s", rs.getString("city"), rs.getInt("count"));
                labels.add(new Label(cityCount));
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        return labels;
    }
    
    //Make the database call to populate the Calendar appropriately.
    private void displayAppointmentsOnCalendar(Month month, int year, AppointmentCalendar c) {
        String select = "SELECT * FROM appointment;";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(select)) {
            while (rs.next()) {
                LocalDateTime day = ((Timestamp) rs.getObject("start")).toLocalDateTime();
                //Convert day from UTC to display calendar in local time zone 
                ZonedDateTime zonedDay = ZonedDateTime.ofInstant(ZonedDateTime.of(day, utc).toInstant(), myZone);
                if (zonedDay.getMonth().equals(month) && zonedDay.getYear() == year) {
                    c.addToDay(zonedDay.getDayOfMonth(), rs.getString("title"));
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Populate the customers list using the database.
    private void loadCustomers() {
        String select = "SELECT * FROM customer";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(select)) {
            //For each customer in db, add to customers list.
            while (rs.next()) {
                Customer c = new Customer();
                c.setCustomerName(rs.getString("customerName"));
                c.setCustomerId(rs.getInt("customerId"));
                c.setActive(rs.getInt("active"));
                c.setAddressId(rs.getInt("addressId"));
                customers.add(c);
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Retrieve address info and store it in the customer object.
    private void loadAddressInfo(Customer selected) {
        String selectAddress = "SELECT * FROM address WHERE addressId='" + selected.getAddressId() + "';";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(selectAddress)) {
            //Get info from adress table.
            if (rs.first()) {
                selected.setAddress(rs.getString("address"));
                selected.setAddress2(rs.getString("address2"));
                selected.setCityId(rs.getInt("cityId"));
                selected.setPhone(rs.getString("phone"));
                selected.setPostalCode(rs.getString("postalCode"));
            }
            String selectCity = "SELECT * FROM city WHERE cityId='" + selected.getCityId() + "';";
            ResultSet rs2 = statement.executeQuery(selectCity);
            //Get info from city table.
            if (rs2.first()) {
                selected.setCity(rs2.getString("city"));
                selected.setCountryId(rs2.getInt("countryId"));
            }
            rs2.close();
            String selectCountry = "SELECT * FROM country WHERE countryId='" + selected.getCountryId() + "';";
            ResultSet rs3 = statement.executeQuery(selectCountry);
            //Get info from country table.
            if (rs3.first()) {
                selected.setCountry(rs3.getString("country"));
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Populate the appointments list using the database.
    private void loadAppointments() {
        String select = "SELECT * FROM appointment";
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(select)) {
            while (rs.next()) {
                Appointment a = new Appointment();
                a.setAppointmentId(rs.getInt("appointmentId"));
                a.setTitle(rs.getString("title"));
                a.setContact(rs.getString("contact"));
                a.setCustomerId(rs.getInt("customerId"));
                a.setDescription(rs.getString("description"));
                a.setLocation(rs.getString("location"));
                a.setUrl(rs.getString("url"));
                a.setCreatedBy(rs.getString("createdBy"));
                //Convert start and end times from current Locale to be saved in UTC in the DB
                LocalDateTime start = ((Timestamp) rs.getObject("start")).toLocalDateTime();
                LocalDateTime end = ((Timestamp) rs.getObject("end")).toLocalDateTime();
                ZonedDateTime zonedStart = ZonedDateTime.ofInstant(ZonedDateTime.of(start, utc).toInstant(), myZone);
                ZonedDateTime zonedEnd = ZonedDateTime.ofInstant(ZonedDateTime.of(end, utc).toInstant(), myZone);
                
                a.setStart(zonedStart.toLocalDateTime());
                a.setEnd(zonedEnd.toLocalDateTime());
                appointments.add(a);
            }
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Update customer info keeping same custName number, update address related info keeping addressId if needed.
    private void updateCustomer(int custId, String name, String address, String phone, String address2, String city, String postal, String country) {
        String findAddress = String.format("SELECT addressId FROM address WHERE address='%s'", address);
        utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
        int addressId;
        
        try (Statement statement = conn.createStatement();
                ResultSet addressRS = statement.executeQuery(findAddress);) {
            //If address is in database keep addressId but ensure update of address2, postalCode, phone
            if (addressRS.last()) {
                addressId = addressRS.getInt("addressId");
                String updateAddress = String.format("UPDATE `U04ASC`.`address` SET `address2`='%s', `postalCode`='%s', `phone`='%s' WHERE `addressId`='%s';", address2, postal, phone, addressId);
                int rowsAffected = statement.executeUpdate(updateAddress);
                System.out.println(rowsAffected + " rows updated.");
            } else {
                //Address not found, insert new address
                addressId = insertAddress(address, address2, city, postal, country, phone);
            }
            //Update customer name and addressId with new info
            String update = String.format("UPDATE `U04ASC`.`customer` SET `customerName`='%s', `addressId`='%s' WHERE `customerId`='%s';", name, addressId, custId);
            int rows = statement.executeUpdate(update);
            System.out.println(rows + " rows updated.");
            log.write(String.format("%s updated %s", user, name));
            log.newLine();
            log.flush();
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Update an Appointment by it's AppointmentId.
    private void updateAppointment(int appointmentId, int customerId, String title, String description,
            String location, String contact, String url, LocalDateTime start, LocalDateTime end) {
        //If there is no matching customer, throw exception
        Optional match = customers.stream().filter(c -> c.getCustomerId() == customerId).findAny();
        if (!match.isPresent()) {
            throw new IllegalArgumentException("There is no customer with id of " + customerId + ".");
        }

        //Convert start and end times from current Locale to be saved in UTC in the DB
        ZonedDateTime zonedStart = ZonedDateTime.ofInstant(ZonedDateTime.of(start, myZone).toInstant(), utc);
        ZonedDateTime zonedEnd = ZonedDateTime.ofInstant(ZonedDateTime.of(end, myZone).toInstant(), utc);
        
        String selectAppointment = "SELECT * FROM appointment;";
        String update = String.format("UPDATE `U04ASC`.`appointment` SET `customerId`='%s', `title`='%s', `description`='%s', `location`='%s', `contact`='%s', `url`='%s', `start`='%s', `end`='%s' WHERE `appointmentId`='%s';",
                customerId, title, description, location, contact, url, zonedStart.toLocalDateTime(), zonedEnd.toLocalDateTime(), appointmentId);
        utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(selectAppointment);) {
            //look through the existing appointments to ensure no time conflict
            while (rs.next()) {
                ZonedDateTime otherStart = ZonedDateTime.of(((Timestamp) rs.getObject("start")).toLocalDateTime(), utc);
                ZonedDateTime otherEnd = ZonedDateTime.of(((Timestamp) rs.getObject("end")).toLocalDateTime(), utc);
                //If appointment overlaps another(check id), throw exception. Overlap may be starting or ending during another appointment or starting before and ending after.
                if (appointmentId != rs.getInt("appointmentId") && ((zonedStart.isAfter(otherStart) && zonedStart.isBefore(otherEnd))
                        || (zonedEnd.isAfter(otherStart) && zonedEnd.isBefore(otherEnd))
                        || (zonedStart.isBefore(otherStart) && zonedEnd.isAfter(otherEnd)))) {
                    LocalTime otherStartTime = otherStart.withZoneSameInstant(myZone).toLocalTime();
                    LocalTime otherEndTime = otherEnd.withZoneSameInstant(myZone).toLocalTime();
                    String message = String.format("These times overlap with another appointment.\n Time Conflict with : %s from %s to %s.", rs.getString("title"), otherStartTime, otherEndTime);
                    throw new IllegalArgumentException(message);
                }
            }
            int rows = statement.executeUpdate(update);
            System.out.println(rows + " rows updated.");
            log.write(String.format("%s updated appointment: %s id: %s", user, title, appointmentId));
            log.newLine();
            log.flush();
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Delete a Customer by it's CustomerId.
    private void deleteCustomer(int selectedCustomerId) {
        String delete = String.format("DELETE FROM `U04ASC`.`customer` WHERE `customerId`='%s';", selectedCustomerId);
        try (Statement statement = conn.createStatement()) {
            int result = statement.executeUpdate(delete);
            //Write to log about deleted record.
            if (result > 0) {
                log.write(String.format("Customer %s deleted from records\n", selectedCustomerId));
                log.newLine();
                log.flush();
            }
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //Delete an Appointment by it's AppointmentId.
    private void deleteAppointment(int selectedAppointmentId) {
        String delete = String.format("DELETE FROM `U04ASC`.`appointment` WHERE `appointmentId`='%s';", selectedAppointmentId);
        try (Statement statement = conn.createStatement()) {
            int result = statement.executeUpdate(delete);
            //Write to log about deleted record.
            if (result > 0) {
                log.write(String.format("%s deleted appointment id: %s from records\n", user, selectedAppointmentId));
                log.newLine();
                log.flush();
            }
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //execute sql to add customer using address if found, else add address.
    private void insertCustomer(String name, String address, String phone, String address2, String city, String postal, String country) {
        String selectCust = "SELECT * FROM customer;";
        String findAddress = String.format("SELECT addressId FROM address WHERE address='%s'", address);
        utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
        int custId, addressId;
        
        try (Statement statement = conn.createStatement();
                ResultSet addressRS = statement.executeQuery(findAddress);) {
            if (addressRS.last()) {
                addressId = addressRS.getInt("addressId");
            } else {
                addressId = insertAddress(address, address2, city, postal, country, phone);
            }
            //Pull the customer table and get the last custName and increment for the new customer.
            ResultSet rs = statement.executeQuery(selectCust);
            if (rs.last()) {
                custId = rs.getInt("customerId") + 1;
            } else {
                //If no records, start customer Id at 1
                custId = 1;
            }
            rs.close();
            String insert = String.format("INSERT INTO `U04ASC`.`customer` "
                    + "(`customerId`, `customerName`, `addressId`, `active`, `createDate`, `createdBy`, `lastUpdateBy`) VALUES "
                    + "('%s', '%s', '%s', '1', '%s', '%s', '%s');", custId, name, addressId, utcNow, user, user);
            int rows = statement.executeUpdate(insert);
            System.out.println(rows + " rows updated.");
            //Write to log about added customer.
            log.write(String.format("%s added %s with Customer Id: %s", user, name, custId));
            log.newLine();
            log.flush();
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //execute sql to add address using city if found, else add city.
    private int insertAddress(String address, String address2, String city, String postal, String country, String phone) {
        String selectAddress = "SELECT * FROM address;";
        String findCity = String.format("SELECT cityId FROM city WHERE city='%s'", city);
        int cityId, addressId = 1;
        
        try (Statement statement = conn.createStatement();
                ResultSet cityRS = statement.executeQuery(findCity);) {
            if (cityRS.last()) {
                cityId = cityRS.getInt("cityId");
            } else {
                cityId = insertCity(city, country);
            }
            //Pull the address table and get the last addressId and increment for the new address.
            ResultSet rs = statement.executeQuery(selectAddress);
            if (rs.last()) {
                addressId = rs.getInt("addressId") + 1;
            } else {
                //If no records, start address Id at 1
                addressId = 1;
            }
            rs.close();
            String insert = String.format("INSERT INTO `U04ASC`.`address` "
                    + "(`addressId`, `address`, `address2`, `cityId`, `postalCode`, `phone`, `createDate`, `createdBy`, `lastUpdateBy`) VALUES"
                    + "('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');", addressId, address, address2, cityId, postal, phone, utcNow, user, user);
            int rows = statement.executeUpdate(insert);
            System.out.println(rows + " rows updated.");
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        return addressId;
    }

    //execute sql to add city using country if found, else add country.
    private int insertCity(String city, String country) {
        String selectCity = "SELECT * FROM city;";
        String findCountry = String.format("SELECT countryID FROM country WHERE country='%s'", country);
        int countryId, cityId = 1;
        
        try (Statement statement = conn.createStatement();
                ResultSet countryRS = statement.executeQuery(findCountry);) {
            if (countryRS.last()) {
                countryId = countryRS.getInt("countryId");
            } else {
                countryId = insertCountry(country);
            }
            //Pull the city table and get the last cityId and increment for the new city.
            ResultSet rs = statement.executeQuery(selectCity);
            if (rs.last()) {
                cityId = rs.getInt("cityId") + 1;
            } else {
                //If no records, start city Id at 1
                cityId = 1;
            }
            rs.close();
            String insert = String.format("INSERT INTO `U04ASC`.`city` "
                    + "(`cityId`, `city`, `countryId`, `createDate`, `createdBy`, `lastUpdateBy`) VALUES"
                    + "('%s', '%s', '%s', '%s', '%s', '%s');", cityId, city, countryId, utcNow, user, user);
            int rows = statement.executeUpdate(insert);
            System.out.println(rows + " rows updated.");
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        return cityId;
    }

    //execute sql to add country.
    private int insertCountry(String country) {
        String selectCountry = "SELECT * FROM country;";
        int countryId = 1;
        
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(selectCountry);) {
            //Pull the country table and get the last countryId and increment for the new country.
            if (rs.last()) {
                countryId = rs.getInt("countryId") + 1;
            } else {
                //If no records, start city Id at 1
                countryId = 1;
            }
            String insert = String.format("INSERT INTO `U04ASC`.`country` "
                    + "(`countryId`, `country`, `createDate`, `createdBy`, `lastUpdateBy`) VALUES"
                    + "('%s', '%s', '%s', '%s', '%s');", countryId, country, utcNow, user, user);
            int rows = statement.executeUpdate(insert);
            System.out.println(rows + " rows updated.");
        } catch (SQLException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
        return countryId;
    }

    //execute sql to add appointment for a customer.
    private void insertAppointment(int customerId, String title, String description, String location, String contact, String url, LocalDateTime start, LocalDateTime end) {
        //If there is no matching customer, throw exception
        Optional match = customers.stream().filter(c -> c.getCustomerId() == customerId).findAny();
        if (!match.isPresent()) {
            throw new IllegalArgumentException("There is no customer with id of " + customerId + ".");
        }
        
        String selectAppointment = "SELECT * FROM appointment;";
        utcNow = LocalDateTime.from(ZonedDateTime.now(utc));
        //Convert start and end times from current Locale to be saved in UTC in the DB
        ZonedDateTime zonedStart = ZonedDateTime.ofInstant(ZonedDateTime.of(start, myZone).toInstant(), utc);
        ZonedDateTime zonedEnd = ZonedDateTime.ofInstant(ZonedDateTime.of(end, myZone).toInstant(), utc);
        
        int appointmentId;
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(selectAppointment);) {
            //find the last appointment so we can set the new id to be greater
            if (rs.last()) {
                appointmentId = rs.getInt("appointmentId") + 1;
            } else {
                appointmentId = 1;
            }
            //look through the appointments to ensure no time conflict
            while (rs.previous()) {
                ZonedDateTime otherStart = ZonedDateTime.of(((Timestamp) rs.getObject("start")).toLocalDateTime(), utc);
                ZonedDateTime otherEnd = ZonedDateTime.of(((Timestamp) rs.getObject("end")).toLocalDateTime(), utc);
                //If new appointment overlaps another, throw exception. Overlap may be starting or ending during another appointment or starting before and ending after.
                if ((zonedStart.isAfter(otherStart) && zonedStart.isBefore(otherEnd))
                        || (zonedEnd.isAfter(otherStart) && zonedEnd.isBefore(otherEnd))
                        || (zonedStart.isBefore(otherStart) && zonedEnd.isAfter(otherEnd))) {
                    LocalTime otherStartTime = otherStart.withZoneSameInstant(myZone).toLocalTime();
                    LocalTime otherEndTime = otherEnd.withZoneSameInstant(myZone).toLocalTime();
                    String message = String.format("These times overlap with another appointment.\n Time Conflict with : %s from %s to %s.", rs.getString("title"), otherStartTime, otherEndTime);
                    throw new IllegalArgumentException(message);
                }
            }
            String insert = String.format("INSERT INTO `U04ASC`.`appointment` "
                    + "(`appointmentId`, `customerId`, `title`, `description`, `location`, `contact`, `url`, `start`, `end`, `createDate`, `createdBy`, `lastUpdateBy`) VALUES "
                    + "('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                    appointmentId, customerId, title, description, location, contact, url, zonedStart.toLocalDateTime(), zonedEnd.toLocalDateTime(), utcNow, user, user);
            statement.executeUpdate(insert);
            //Write to log about inserted appointment.
            log.write(String.format("%s added %s with Customer Id: %s", user, title, customerId));
            log.newLine();
            log.flush();
        } catch (SQLException | IOException ex) {
            Logger.getLogger(GlobalConsultingScheduling.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
