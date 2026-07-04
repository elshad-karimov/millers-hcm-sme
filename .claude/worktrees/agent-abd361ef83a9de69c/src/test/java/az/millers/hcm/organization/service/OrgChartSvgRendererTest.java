package az.millers.hcm.organization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.organization.api.dto.OrgTreeNode;
import az.millers.hcm.organization.domain.OrgUnitType;
/**
 * Unit tests for {@link OrgChartSvgRenderer}. No Spring context — the
 * renderer is a pure function on the tree.
 */
class OrgChartSvgRendererTest {

    private final OrgChartSvgRenderer renderer = new OrgChartSvgRenderer();

    private static OrgTreeNode node(String code, String name, String type,
                                     OrgTreeNode... kids) {
        List<OrgTreeNode> list = new ArrayList<>();
        for (OrgTreeNode k : kids) list.add(k);
        return new OrgTreeNode(UUID.randomUUID(), code, name, type,
                null, null, 0, list);
    }

    @Test
    void rendersEmptyMessageOnNullTree() {
        String svg = renderer.render(null, "anything");
        assertThat(svg).contains("<svg").contains("No active org structure version");
    }

    @Test
    void rendersSingleRootWithNoChildren() {
        OrgTreeNode root = node("ACME", "Acme Inc.", OrgUnitType.COMPANY);
        String svg = renderer.render(root, "Acme org chart");
        assertThat(svg)
                .startsWith("<svg")
                .endsWith("</svg>")
                .contains("Acme Inc.")
                .contains("ACME")
                .contains("Acme org chart");
        // One node rect for the root.
        assertThat(svg.split("class=\"node-rect", -1).length - 1).isEqualTo(1);
    }

    @Test
    void rendersTreeWithEdges() {
        OrgTreeNode root = node("ACME", "Acme Inc.", OrgUnitType.COMPANY,
                node("ENG", "Engineering", OrgUnitType.DIVISION,
                        node("BE", "Backend", OrgUnitType.TEAM),
                        node("FE", "Frontend", OrgUnitType.TEAM)),
                node("FIN", "Finance", OrgUnitType.DIVISION));
        String svg = renderer.render(root, "test");
        // 5 nodes total
        assertThat(svg.split("class=\"node-rect", -1).length - 1).isEqualTo(5);
        // 4 edges (root→ENG, root→FIN, ENG→BE, ENG→FE)
        assertThat(svg.split("class=\"edge\"", -1).length - 1).isEqualTo(4);
        // Team styling applied to TEAM-type nodes
        assertThat(svg).contains("node-rect-team");
        // Names present
        assertThat(svg).contains("Engineering").contains("Backend").contains("Frontend")
                .contains("Finance");
    }

    @Test
    void escapesXmlCharactersInUnitNames() {
        OrgTreeNode root = node("X&Y", "Marketing & PR <2026>",
                OrgUnitType.DEPARTMENT);
        String svg = renderer.render(root, null);
        assertThat(svg)
                .contains("Marketing &amp; PR &lt;2026&gt;")
                .contains("X&amp;Y")
                .doesNotContain("Marketing & PR <2026>");
    }

    @Test
    void truncatesLongUnitNames() {
        String longName = "A".repeat(50);
        OrgTreeNode root = node("LONG", longName, OrgUnitType.UNIT);
        String svg = renderer.render(root, null);
        // Truncated to 28 chars including the ellipsis.
        assertThat(svg).contains("A".repeat(27) + "…");
    }
}
