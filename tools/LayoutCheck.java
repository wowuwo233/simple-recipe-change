import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 布局数值检查（不依赖 Minecraft）。
 *
 * <p>直接解析两个源文件里的版面常量，把几何约束代进去验证。这样既不需要启动游戏，
 * 也不需要 MC 的类路径——过去反复出现的「背包被切 / 备注压住副产物 / 按钮压住输入框」
 * 都属于纯几何问题，用断言比截图快得多。
 */
public final class LayoutCheck {

    private static final int SLOT = 18;
    private static final int BUTTON_H = 20;
    private static final int FIELD_H = 18;

    private static int pass;
    private static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Map<String, Integer> menu = constants(
                Path.of("src/main/java/com/wowuwo233/simple_recipe_change/menu/RecipeEditorMenu.java"));
        Map<String, Integer> screen = constants(
                Path.of("src/main/java/com/wowuwo233/simple_recipe_change/client/RecipeEditorScreen.java"));

        int width = menu.get("IMAGE_WIDTH");
        int height = menu.get("IMAGE_HEIGHT");

        // ---- 1. 背包与快捷栏 ----
        int inventoryBottom = menu.get("PLAYER_Y") + 3 * SLOT + 2;
        int hotbarTop = menu.get("HOTBAR_Y");
        check("背包底边在窗口内", inventoryBottom <= height,
                "背包底 " + inventoryBottom + " > 窗口高 " + height);
        check("快捷栏在窗口内", hotbarTop + SLOT <= height,
                "快捷栏底 " + (hotbarTop + SLOT) + " > 窗口高 " + height);
        check("背包不压快捷栏", inventoryBottom <= hotbarTop,
                "背包底 " + inventoryBottom + " 压住快捷栏顶 " + hotbarTop);

        // ---- 2. 背包居中 ----
        int inventoryWidth = 9 * SLOT;
        int leftGap = menu.get("PLAYER_X");
        int rightGap = width - menu.get("PLAYER_X") - inventoryWidth;
        check("背包水平居中", Math.abs(leftGap - rightGap) <= 2,
                "左边距 " + leftGap + " / 右边距 " + rightGap);

        // ---- 3. 槽位常量在窗口内 ----
        String[] xNames = {"ADD_GRID_X", "ADD_OUTPUT_X", "MOD_GRID_X", "MOD_OUTPUT_X",
                "COOK_INPUT_X", "COOK_OUTPUT_X", "SMITH_TEMPLATE_X", "SMITH_BASE_X",
                "SMITH_ADDITION_X", "SMITH_OUTPUT_X", "COOK_GRID_X", "COOK_CONTAINER_X",
                "CUT_INPUT_X", "CUT_TOOL_X", "FARMERS_OUTPUT_X", "CREATE_GRID_X",
                "CREATE_OUTPUT_X", "SEQ_INPUT_X", "SEQ_TRANSITIONAL_X", "SEQ_OUTPUT_X",
                "SEQ_FAIL_X", "EXTRA_SLOT_X0"};
        for (String name : xNames) {
            Integer value = menu.get(name);
            if (value == null) {
                continue;
            }
            check(name + " 在窗口内", value >= 0 && value + SLOT <= width,
                    "x=" + value + "，右边到 " + (value + SLOT) + " 超出 " + width);
        }

        // ---- 4. 纵向区块互不重叠 ----
        int contentBottom = menu.get("CREATE_EXTRA_ROW_Y") + SLOT;
        int noteLabel = screen.get("NOTE_LABEL_Y");
        int noteTop = screen.get("NOTE_Y");
        int noteBottom = noteTop + FIELD_H;
        int found = screen.get("FOUND_Y");
        int controlsTop = screen.get("CONTROLS_Y");
        int controlsBottom = controlsTop + BUTTON_H;
        int labelY = screen.get("LABEL_Y");
        int fieldTop = screen.get("FIELD_Y");
        int fieldBottom = fieldTop + FIELD_H;
        int buttonsTop = screen.get("BUTTONS_Y");
        int buttonsBottom = buttonsTop + BUTTON_H;
        int playerTop = menu.get("PLAYER_Y");

        check("备注标签在内容区之下", noteLabel >= contentBottom,
                "标签 " + noteLabel + " 压住内容区底 " + contentBottom);
        check("备注框在标签之下", noteTop >= noteLabel + 8,
                "备注框 " + noteTop + " 与标签 " + noteLabel + " 重叠");
        // 信息行现在和「备注」标签同一行（画在右侧），所以只要在标签行内即可
        check("信息行与备注标签同行", found >= noteLabel && found < noteTop,
                "信息行 " + found + " 不在标签行 " + noteLabel + ".." + noteTop);
        check("类型行在备注框之下", controlsTop >= noteBottom,
                "类型行 " + controlsTop + " 压住备注底 " + noteBottom);
        check("配方名标签在类型行之下", labelY >= controlsBottom,
                "标签 " + labelY + " 压住类型行底 " + controlsBottom);
        check("输入框在标签之下", fieldTop >= labelY + 8,
                "输入框 " + fieldTop + " 压住标签 " + labelY);
        check("底栏在输入框之下", buttonsTop >= fieldBottom,
                "底栏 " + buttonsTop + " 压住输入框底 " + fieldBottom);
        check("背包在底栏之下", playerTop >= buttonsBottom,
                "背包 " + playerTop + " 压住底栏底 " + buttonsBottom);

        // ---- 5. 搜索浮层在窗口内 ----
        check("搜索浮层在窗口内", screen.get("OVERLAY_W") <= width && screen.get("OVERLAY_H") <= height,
                "浮层 " + screen.get("OVERLAY_W") + "x" + screen.get("OVERLAY_H"));

        System.out.println();
        System.out.println("==================================================");
        System.out.println("通过 " + pass + " / 失败 " + problems.size());
        for (String p : problems) {
            System.out.println("  [FAIL] " + p);
        }
        System.out.println(problems.isEmpty() ? "LAYOUT OK" : "LAYOUT HAS PROBLEMS");
    }

    /** 从源文件里抽出 public/private static final int NAME = 数字; */
    private static Map<String, Integer> constants(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Integer> map = new HashMap<>();
        Matcher m = Pattern.compile(
                "(?:public|private|protected)?\\s*static\\s+final\\s+int\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;")
                .matcher(source);
        while (m.find()) {
            map.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        return map;
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            problems.add(what + " -> " + detail);
        }
    }

    private LayoutCheck() {
    }
}
