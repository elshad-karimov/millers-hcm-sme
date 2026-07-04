package az.millers.hcm.organization.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import az.millers.hcm.organization.api.dto.OrgTreeNode;
import az.millers.hcm.organization.domain.OrgUnitType;

/**
 * Renders an {@link OrgTreeNode} as a layered top-down SVG (M83).
 *
 * <p>Algorithm: two passes —
 * <ol>
 *   <li>post-order — compute each subtree's pixel width (sum of children
 *       widths + spacing, or a base width for leaves)</li>
 *   <li>pre-order — assign (x, y) using the subtree widths so siblings
 *       don't overlap</li>
 * </ol>
 *
 * <p>The output is self-contained vanilla SVG (no embedded JS, no
 * external assets) so it works in any browser, can be opened directly
 * by a PDF renderer like wkhtmltopdf, and is safe to email.
 */
@Service
public class OrgChartSvgRenderer {

    private static final int NODE_WIDTH = 200;
    private static final int NODE_HEIGHT = 70;
    private static final int H_SPACING = 24;
    private static final int V_SPACING = 60;
    private static final int CANVAS_PAD = 32;

    /** Layout metadata for one node — populated during the two-pass walk. */
    private record Box(OrgTreeNode node, int subtreeWidth, int x, int y) {}

    public String render(OrgTreeNode root, String title) {
        if (root == null) return emptySvg();

        Map<Object, Integer> widths = new HashMap<>();
        Map<Object, Box> placed = new HashMap<>();

        // Pass 1: subtree widths.
        int totalWidth = computeWidth(root, widths);

        // Pass 2: assign coordinates.
        int canvasX = CANVAS_PAD + totalWidth;
        int canvasY = CANVAS_PAD;
        assignCoords(root, CANVAS_PAD, canvasY, widths, placed);

        int canvasW = CANVAS_PAD * 2 + totalWidth;
        int canvasH = CANVAS_PAD * 2 + depth(root) * (NODE_HEIGHT + V_SPACING) + NODE_HEIGHT;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" ")
                .append("width=\"").append(canvasW).append("\" height=\"").append(canvasH)
                .append("\" viewBox=\"0 0 ").append(canvasW).append(' ').append(canvasH).append("\">");
        sb.append("<style>")
                .append(".unit{font-family:'-apple-system',Helvetica,Arial,sans-serif;font-size:13px;}")
                .append(".unit-type{font-size:10px;fill:#666;text-transform:uppercase;letter-spacing:0.05em;}")
                .append(".unit-code{font-size:10px;fill:#999;}")
                .append(".edge{fill:none;stroke:#bbb;stroke-width:1.2;}")
                .append(".node-rect{stroke:#1677ff;stroke-width:1.5;fill:#f0f7ff;}")
                .append(".node-rect-team{fill:#fff;}")
                .append(".title{font-family:'-apple-system',Helvetica,Arial,sans-serif;font-size:18px;font-weight:600;}")
                .append("</style>");
        if (title != null && !title.isBlank()) {
            sb.append("<text class=\"title\" x=\"").append(CANVAS_PAD)
                    .append("\" y=\"24\">").append(xmlEscape(title)).append("</text>");
        }

        // Edges first so node rectangles paint on top.
        drawEdges(root, placed, sb);
        // Nodes
        drawNodes(root, placed, sb);

        sb.append("</svg>");
        // Suppress unused warning for canvasX (kept for symmetry / future use)
        if (false) { System.out.println(canvasX); }
        return sb.toString();
    }

    // ── Pass 1: width ────────────────────────────────────────────────────────

    private int computeWidth(OrgTreeNode n, Map<Object, Integer> widths) {
        List<OrgTreeNode> kids = n.children();
        int w;
        if (kids == null || kids.isEmpty()) {
            w = NODE_WIDTH;
        } else {
            int childSum = 0;
            for (int i = 0; i < kids.size(); i++) {
                childSum += computeWidth(kids.get(i), widths);
                if (i < kids.size() - 1) childSum += H_SPACING;
            }
            w = Math.max(NODE_WIDTH, childSum);
        }
        widths.put(n.id(), w);
        return w;
    }

    // ── Pass 2: place ────────────────────────────────────────────────────────

    private void assignCoords(OrgTreeNode n, int xStart, int y,
                               Map<Object, Integer> widths, Map<Object, Box> placed) {
        int w = widths.getOrDefault(n.id(), NODE_WIDTH);
        int nodeX = xStart + (w - NODE_WIDTH) / 2;
        placed.put(n.id(), new Box(n, w, nodeX, y));

        List<OrgTreeNode> kids = n.children();
        if (kids == null || kids.isEmpty()) return;
        int cursor = xStart;
        int childY = y + NODE_HEIGHT + V_SPACING;
        for (OrgTreeNode kid : kids) {
            int kidW = widths.getOrDefault(kid.id(), NODE_WIDTH);
            assignCoords(kid, cursor, childY, widths, placed);
            cursor += kidW + H_SPACING;
        }
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    private void drawEdges(OrgTreeNode n, Map<Object, Box> placed, StringBuilder sb) {
        Box parent = placed.get(n.id());
        if (parent == null) return;
        int px = parent.x() + NODE_WIDTH / 2;
        int py = parent.y() + NODE_HEIGHT;
        List<OrgTreeNode> kids = n.children();
        if (kids == null) return;
        for (OrgTreeNode kid : kids) {
            Box k = placed.get(kid.id());
            if (k == null) continue;
            int kx = k.x() + NODE_WIDTH / 2;
            int ky = k.y();
            int midY = (py + ky) / 2;
            sb.append("<path class=\"edge\" d=\"M ").append(px).append(' ').append(py)
                    .append(" L ").append(px).append(' ').append(midY)
                    .append(" L ").append(kx).append(' ').append(midY)
                    .append(" L ").append(kx).append(' ').append(ky).append("\"/>");
            drawEdges(kid, placed, sb);
        }
    }

    private void drawNodes(OrgTreeNode n, Map<Object, Box> placed, StringBuilder sb) {
        Box b = placed.get(n.id());
        if (b == null) return;
        String rectClass = OrgUnitType.TEAM.equals(n.unitType()) ? "node-rect node-rect-team" : "node-rect";
        sb.append("<g class=\"unit\">");
        sb.append("<rect class=\"").append(rectClass).append("\" x=\"").append(b.x())
                .append("\" y=\"").append(b.y())
                .append("\" width=\"").append(NODE_WIDTH).append("\" height=\"").append(NODE_HEIGHT)
                .append("\" rx=\"8\"/>");
        sb.append("<text class=\"unit-type\" x=\"").append(b.x() + 12)
                .append("\" y=\"").append(b.y() + 18).append("\">")
                .append(n.unitType()).append("</text>");
        sb.append("<text x=\"").append(b.x() + 12)
                .append("\" y=\"").append(b.y() + 40).append("\">")
                .append(xmlEscape(truncate(n.name(), 28))).append("</text>");
        sb.append("<text class=\"unit-code\" x=\"").append(b.x() + 12)
                .append("\" y=\"").append(b.y() + 56).append("\">")
                .append(xmlEscape(n.code())).append("</text>");
        sb.append("</g>");
        List<OrgTreeNode> kids = n.children();
        if (kids != null) for (OrgTreeNode kid : kids) drawNodes(kid, placed, sb);
    }

    private int depth(OrgTreeNode n) {
        if (n.children() == null || n.children().isEmpty()) return 0;
        int max = 0;
        for (OrgTreeNode kid : n.children()) max = Math.max(max, depth(kid));
        return 1 + max;
    }

    private static String emptySvg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"60\">"
                + "<text x=\"12\" y=\"32\" font-family=\"sans-serif\" font-size=\"14\">"
                + "No active org structure version</text></svg>";
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // Kept private to satisfy IDE warnings about unused helper.
    @SuppressWarnings("unused")
    private List<OrgTreeNode> flatten(OrgTreeNode n) {
        List<OrgTreeNode> out = new ArrayList<>();
        out.add(n);
        if (n.children() != null) for (OrgTreeNode c : n.children()) out.addAll(flatten(c));
        return out;
    }
}
