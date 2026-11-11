package com.uenav.client;

import com.uenav.core.GraphCheck;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Map health page: checks the map the phones download (GraphCheck) for places nothing connects to, floors without
 * stairs or elevators, places with no step-free route, odd walkway distances, duplicate names and offices
 * without hours. Fix them on the Places and Walkways pages, then press "Check again".
 */
public class MapHealthView extends ScrollPane {

    private final AdminApi api;
    private final VBox rows = new VBox(10);
    private final Label summary = new Label();
    private final Button again = new Button("Check again");

    public MapHealthView(AdminApi api) {
        this.api = api;
        setFitToWidth(true);
        getStyleClass().add("canvas");

        Label eyebrow = new Label("TOOLS");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Map health");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Mistakes in the map that would give visitors wrong or missing routes.");
        sub.getStyleClass().add("muted");
        again.getStyleClass().add("secondary-button");
        again.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, new VBox(2, eyebrow, title, sub), spacer, again);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox summaryRow = new HBox(summary);
        VBox page = new VBox(16, header, summaryRow, rows);
        page.setPadding(new Insets(24));
        setContent(page);
        load();
    }

    private void load() {
        again.setDisable(true);
        summary.setText("Checking…");
        summary.getStyleClass().setAll("label", "chip");
        rows.getChildren().clear();
        Async.run(() -> GraphCheck.check(api.snapshot()), this::show, e -> {
            again.setDisable(false);
            summary.setText(e.getMessage());
            summary.getStyleClass().setAll("label", "chip", "chip-bad");
        });
    }

    private void show(List<GraphCheck.Issue> issues) {
        again.setDisable(false);
        summary.setText(summaryText(issues));
        summary.getStyleClass().setAll("label", "chip", chipClass(issues));
        rows.getChildren().clear();
        if (issues.isEmpty()) {
            rows.getChildren().add(card(issueRow(new GraphCheck.Issue(GraphCheck.Severity.WARNING,
                    "No problems found", "Every place can be reached and the walkways look right.", List.of()), true)));
            return;
        }
        for (GraphCheck.Issue i : issues) {
            rows.getChildren().add(card(issueRow(i, false)));
        }
    }

    static String summaryText(List<GraphCheck.Issue> issues) {
        long problems = issues.stream().filter(i -> i.severity() == GraphCheck.Severity.PROBLEM).count();
        long warnings = issues.size() - problems;
        if (issues.isEmpty()) {
            return "Map looks healthy";
        }
        StringBuilder sb = new StringBuilder();
        if (problems > 0) {
            sb.append(problems).append(problems == 1 ? " problem" : " problems");
        }
        if (warnings > 0) {
            sb.append(sb.length() > 0 ? " · " : "").append(warnings).append(warnings == 1 ? " warning" : " warnings");
        }
        return sb.toString();
    }

    static String chipClass(List<GraphCheck.Issue> issues) {
        if (issues.isEmpty()) {
            return "chip-ok";
        }
        return issues.stream().anyMatch(i -> i.severity() == GraphCheck.Severity.PROBLEM) ? "chip-bad" : "chip-warn";
    }

    /** One finding: icon, title and what to do. Also used by the dashboard card. */
    static HBox issueRow(GraphCheck.Issue i, boolean healthy) {
        String icon = healthy ? "check" : i.severity() == GraphCheck.Severity.PROBLEM ? "warning" : "info";
        String tone = healthy ? "green" : i.severity() == GraphCheck.Severity.PROBLEM ? "red" : "orange";
        StackPane bubble = new StackPane(Icons.icon(icon, 16));
        bubble.getStyleClass().addAll("nv-bubble", "nv-tone-" + tone);
        bubble.setMinSize(34, 34);
        bubble.setMaxSize(34, 34);
        Label t = new Label(i.title());
        t.getStyleClass().add("card-title");
        t.setWrapText(true);
        Label d = new Label(i.detail());
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        VBox text = new VBox(2, t, d);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, bubble, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("issue-row");
        return row;
    }

    private static VBox card(HBox row) {
        VBox c = new VBox(row);
        c.getStyleClass().add("card");
        c.setPadding(new Insets(14, 18, 14, 18));
        return c;
    }

    /** For the dashboard card: the first few findings, or a "healthy" line. */
    static void fillSummary(VBox target, Label chip, List<GraphCheck.Issue> issues, int max) {
        chip.setText(summaryText(issues));
        chip.getStyleClass().setAll("label", "chip", chipClass(issues));
        target.getChildren().clear();
        if (issues.isEmpty()) {
            target.getChildren().add(issueRow(new GraphCheck.Issue(GraphCheck.Severity.WARNING, "No problems found",
                    "Every place can be reached and the walkways look right.", List.of()), true));
            return;
        }
        for (int k = 0; k < Math.min(max, issues.size()); k++) {
            target.getChildren().add(issueRow(issues.get(k), false));
        }
    }
}
