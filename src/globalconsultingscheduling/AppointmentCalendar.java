package globalconsultingscheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * AppointmentCalendar is an implementation of javaFx that can
 * show a calendar by month and week and receive details to add to each date.
 * @author Paul Brassard
 */
public class AppointmentCalendar {

    private enum Option{
        MONTH, WEEK
    }
    
    private final GridPane pane, grid;
    private Month currentMonth;
    private final HBox weekBox;
    private final Insets insets;
    private final int cell = 85;
    private int currentYear, weekStart, weekEnd;
    private Option option;

    public int getCurrentYear() {
        return currentYear;
    }

    public void setCurrentYear(int currentYear) {
        this.currentYear = currentYear;
        createGrid();
    }

    public AppointmentCalendar() {
        weekStart = 1;
        weekEnd = 7;
        option = Option.MONTH;
        insets = new Insets(0, 15, 0, 10);
        currentMonth = Month.JANUARY;
        currentYear = LocalDate.now().getYear();
        pane = new GridPane();
        grid = new GridPane();
        pane.setId("calendarPane");
        weekBox = new HBox(70);
        weekBox.setPadding(insets);
        //DayOfWeek enum happens to i on Monday, yet I want my calendar starting sunday. So I first add Sunday.
        weekBox.getChildren().add(new Label(DayOfWeek.SUNDAY.toString().substring(0, 3)));
        //Then I limit the stream to 6 so Sunday isn't added twice.
        Arrays.asList(DayOfWeek.values()).stream().limit(6).forEach(d -> {
            weekBox.getChildren().add(new Label(d.toString().substring(0, 3)));
        });
        createGrid();
        pane.add(weekBox, 0, 1, 7, 1);
        pane.add(grid, 0, 3, 7, 5);
    }

    public Month getMonth(){
        return currentMonth;
    }
    
    //Create the grid that holds the days.
    private void createGrid() {
        grid.getChildren().clear();
        int rows = 0, columns = 7;
        if(option.equals(Option.MONTH)){
        rows = 5;
        }
        if(option.equals(Option.WEEK)){
        rows = 1;
        }
        for (int i = 0; i < rows; i++) {
            RowConstraints row = new RowConstraints(cell);
            grid.getRowConstraints().add(row);
        }
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints(cell);
            grid.getColumnConstraints().add(column);
        }
        grid.setId("dayGrid");
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(insets);
        LocalDate first = LocalDate.of(currentYear, currentMonth, 1);
        DayOfWeek weekDay = DayOfWeek.from(first);
        //DayOfWeek.SUNDAY has value 7 so choose 0 instead, other values are good.
        int column = weekDay.equals(DayOfWeek.SUNDAY) ? 0 : weekDay.getValue();
        int row = 0;
        //If viewing month go full month length, otherwise go to end of selected week but not further than the month has
        int length = option.equals(Option.MONTH) ? currentMonth.length(false) : (weekEnd > currentMonth.length(false)) ? currentMonth.length(false) : weekEnd;
        int i = option.equals(Option.MONTH) ? 1 : weekStart;
        for (; i <= length; i++) {
            Label dayLabel = new Label("" + i);
            dayLabel.setAlignment(Pos.TOP_LEFT);
            VBox v = new VBox();
            v.setAlignment(Pos.TOP_LEFT);
            v.getChildren().add(dayLabel);
            grid.add(v, column, row);
            if (column == 6) {
                row++;
                column = 0;
            } else {
                column++;
            }
        }
        grid.setMinSize(100, 100);
    }

    //Change week selection to next week.
    public boolean nextWeek(){
        if(weekEnd < currentMonth.length(false)){
        weekStart += 7;
        weekEnd += 7;
        createGrid();
        return true;
        }
        return false;
    }
    
    //Change week selection to previous week.
    public boolean previousWeek(){
        if(weekStart != 1){
        weekStart -= 7;
        weekEnd -= 7;
        createGrid();
        return true;
        }
        return false;
    }
    
    //Add string information to specified day on the calendar
    public void addToDay(int day, String text) throws IllegalArgumentException{
        if(day > currentMonth.length(false)){
            throw new IllegalArgumentException("Day cannot be greater than days in the month.");
        }
        Label label = new Label(text);
        label.setFont(Font.font("Arial", 10));
        if(option.equals(Option.MONTH)){
        ((VBox) grid.getChildren().get(day - 1)).getChildren().add(label);
        }else if(((day >= weekStart) && (day <= weekEnd))){
        //If viewing by week, don't try to add anything not in view
        ((VBox) grid.getChildren().get(day - weekStart)).getChildren().add(label);
        }
    }

    public void setMonth(Month m) {
        currentMonth = m;
        createGrid();
    }

    //Get the calendar's GridPane for ease of use.
    public GridPane getAsGridPane() {
        return pane;
    }
    
    //change view to month.
    public void viewAsMonth(){
        option = Option.MONTH;
        createGrid();
    }
    
    //change view to week.
    public void viewAsWeek(){
        option = Option.WEEK;
        createGrid();
    }
}
