package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRequestToken;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 执行真实设置 Screen 的 init、输入 responder、保存、tick、resize 重建和退出路径。
 * 仅隔离网络、字体度量和 Minecraft 外壳，不启动窗口或渲染；真实字体/像素仍须实机验收。
 */
final class RtsModConfigScreenInteractionTest {
    private MockedStatic<Config> config;
    private MockedStatic<Minecraft> minecraftStatic;
    private MockedStatic<RtsClientPacketGateway> gateway;
    private Minecraft minecraft;
    private RtsModConfigScreen screen;
    private final AtomicReference<RtsServerConfigView> incoming = new AtomicReference<>();
    private long session;
    private final RtsServerConfigRequestToken saveToken = new RtsServerConfigRequestToken(101L, 2, 5);

    @BeforeEach
    void openRealScreenWithIsolatedPorts() throws Exception {
        config = mockStatic(Config.class);
        minecraft = mock(Minecraft.class);
        minecraftStatic = mockStatic(Minecraft.class);
        minecraftStatic.when(Minecraft::getInstance).thenReturn(minecraft);
        minecraft.level = mock(ClientLevel.class);
        Font font = mock(Font.class);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean()))
                .thenAnswer(call -> call.getArgument(0));
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        gateway = mockStatic(RtsClientPacketGateway.class);
        session = 101L;
        incoming.set(authoritativeView(5, true));
        gateway.when(RtsClientPacketGateway::serverConfigSessionId).thenAnswer(call -> session);
        gateway.when(RtsClientPacketGateway::currentServerConfig).thenAnswer(call -> incoming.get());
        gateway.when(RtsClientPacketGateway::requestServerConfig)
                .thenAnswer(call -> new RtsServerConfigRequestToken(session, 1, incoming.get().revision()));
        gateway.when(() -> RtsClientPacketGateway.saveServerConfig(anyInt(), anyList()))
                .thenReturn(saveToken);
        screen = new RtsModConfigScreen(null);
        set(Screen.class, screen, "minecraft", minecraft);
        set(Screen.class, screen, "font", font);
        screen.width = 800;
        screen.height = 480;
        screen.init();
        ((Button) screen.children().get(1)).onPress();
    }

    @AfterEach
    void closePorts() {
        if (gateway != null) gateway.close();
        if (minecraftStatic != null) minecraftStatic.close();
        if (config != null) config.close();
    }

    @Test
    void inputEnablesSaveAndResizeKeepsDraftAndPendingIdentity() throws Exception {
        assertFalse(saveButton().active);
        actionRadiusBox().setValue("192");
        assertTrue(saveButton().active, "真实 EditBox responder 必须立即启用保存");
        screen.width = 540;
        screen.init();
        assertEquals("192", actionRadiusBox().getValue());
        assertTrue(saveButton().active);
        saveButton().onPress();
        assertEquals(saveToken, get("pendingSave"));
        assertFalse(saveButton().active);
        screen.height = 440;
        screen.init();
        assertEquals(saveToken, get("pendingSave"), "resize 不能丢失在途 ACK 身份");
        assertEquals("192", actionRadiusBox().getValue());
        gateway.verify(RtsClientPacketGateway::requestServerConfig, times(1));
        gateway.verify(() -> RtsClientPacketGateway.saveServerConfig(eq(5), argThat(changes ->
                changes.size() == 1 && changes.get(0).key() == RtsServerConfigChange.Key.MAX_ACTION_RADIUS_BLOCKS
                        && changes.get(0).value().equals(new RtsServerConfigChange.IntValue(192)))), times(1));
    }

    @Test
    void failedAckPreservesDraftAndExactSuccessDoesNotRollBackNewBroadcast() throws Exception {
        actionRadiusBox().setValue("192");
        saveButton().onPress();
        gateway.when(RtsClientPacketGateway::lastServerConfigResultSessionId).thenReturn(101L);
        gateway.when(RtsClientPacketGateway::lastServerConfigResultRequestId).thenReturn(2);
        gateway.when(RtsClientPacketGateway::lastServerConfigResult)
                .thenReturn(RtsServerConfigUpdateResult.saveFailed(incoming.get(), "fixture"));
        screen.tick();
        assertNull(get("pendingSave"));
        assertEquals("192", actionRadiusBox().getValue());
        assertTrue(saveButton().active);
        saveButton().onPress();
        incoming.set(authoritativeView(8, true));
        gateway.when(RtsClientPacketGateway::lastServerConfigResult)
                .thenReturn(RtsServerConfigUpdateResult.applied(authoritativeView(6, true)));
        screen.tick();
        assertNull(get("pendingSave"));
        assertEquals(8, ((RtsServerConfigView) get("latestServerView")).revision());
        assertFalse(saveButton().active);
    }

    @Test
    void newConnectionDropsOldDraftAndEscapeNeverSubmitsIt() throws Exception {
        actionRadiusBox().setValue("192");
        session = 202L;
        incoming.set(authoritativeView(1, true));
        screen.tick();
        assertEquals("128", actionRadiusBox().getValue(), "新连接不能继承旧世界草稿");
        actionRadiusBox().setValue("224");
        assertTrue(screen.keyPressed(256, 0, 0));
        verify(minecraft).setScreen(null);
        gateway.verify(() -> RtsClientPacketGateway.saveServerConfig(anyInt(), anyList()), never());
    }

    @Test
    void revokedPermissionAndLeavingWorldDisableActualSavePath() throws Exception {
        actionRadiusBox().setValue("192");
        incoming.set(authoritativeView(5, false));
        screen.tick();
        assertFalse(saveButton().active);
        saveButton().onPress();
        gateway.verify(() -> RtsClientPacketGateway.saveServerConfig(anyInt(), anyList()), never());
        minecraft.level = null;
        screen.tick();
        assertFalse(saveButton().active);
        assertNull(get("pendingSave"));
        assertFalse(((RtsServerConfigView) get("latestServerView")).worldLoaded());
    }

    private Button saveButton() throws Exception {
        return (Button) get("saveButton");
    }

    @SuppressWarnings("unchecked")
    private EditBox actionRadiusBox() throws Exception {
        return ((Map<RtsServerConfigField, EditBox>) get("worldBoxes")).get(RtsServerConfigField.ACTION_RADIUS);
    }

    private Object get(String name) throws Exception {
        Field field = RtsModConfigScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static RtsServerConfigView authoritativeView(int revision, boolean editable) throws Exception {
        var components = RtsServerConfigView.class.getRecordComponents();
        Object[] values = new Object[components.length];
        RtsServerConfigView defaults = RtsServerConfigView.defaults();
        for (int i = 0; i < components.length; i++) {
            values[i] = switch (components[i].getName()) {
                case "revision" -> revision;
                case "editable" -> editable;
                case "worldLoaded" -> true;
                default -> components[i].getAccessor().invoke(defaults);
            };
        }
        return RtsServerConfigView.class.getDeclaredConstructor(
                Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new))
                .newInstance(values);
    }
}
