package com.rtsbuilding.rtsbuilding.compat.ftb;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class RtsFtbCompatImplTest {
    @Test
    void optionalAccessorReturnsUsableQuestFileReceiver() throws ReflectiveOperationException {
        Object resolved = resolveWithAccessor("optionalInstance");

        assertSame(QuestFileFixture.INSTANCE, resolved);
        assertSame(QuestFileFixture.TEAM_DATA,
                QuestFileFixture.class.getMethod("getOrCreateTeamData").invoke(resolved));
    }

    @Test
    void emptyOptionalMeansQuestFileUnavailable() throws ReflectiveOperationException {
        assertNull(resolveWithAccessor("emptyInstance"));
    }

    @Test
    void legacyDirectAccessorKeepsInstance() throws ReflectiveOperationException {
        assertSame(QuestFileFixture.INSTANCE, resolveWithAccessor("directInstance"));
    }

    @Test
    void legacyNullAccessorMeansQuestFileUnavailable() throws ReflectiveOperationException {
        assertNull(resolveWithAccessor("nullInstance"));
    }

    @Test
    void legacyInstanceFieldStillWorks() throws ReflectiveOperationException {
        RtsFtbCompatImpl compat = mock(RtsFtbCompatImpl.class);
        setField(compat, "serverQuestFileInstanceField", QuestFileFixture.class.getField("INSTANCE"));

        assertSame(QuestFileFixture.INSTANCE, resolve(compat));
    }

    private static Object resolveWithAccessor(String accessor) throws ReflectiveOperationException {
        // 仅绕过会加载外部 FTB 类的构造器；下方调用真实 resolver 和真实反射 accessor。
        RtsFtbCompatImpl compat = mock(RtsFtbCompatImpl.class);
        setField(compat, "serverQuestFileGetInstanceMethod", QuestFileFixture.class.getMethod(accessor));
        return resolve(compat);
    }

    private static Object resolve(RtsFtbCompatImpl compat) throws ReflectiveOperationException {
        Method resolver = RtsFtbCompatImpl.class.getDeclaredMethod("resolveServerQuestFileInstance");
        resolver.setAccessible(true);
        return resolver.invoke(compat);
    }

    private static void setField(RtsFtbCompatImpl compat, String name, Object value)
            throws ReflectiveOperationException {
        Field field = RtsFtbCompatImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(compat, value);
    }

    /** 模拟 FTB 两代 accessor 的返回形状，不向测试 classpath 注册假的 FTB 模组。 */
    public static final class QuestFileFixture {
        public static final QuestFileFixture INSTANCE = new QuestFileFixture();
        public static final Object TEAM_DATA = new Object();

        public static Optional<QuestFileFixture> optionalInstance() {
            return Optional.of(INSTANCE);
        }

        public static Optional<QuestFileFixture> emptyInstance() {
            return Optional.empty();
        }

        public static QuestFileFixture directInstance() {
            return INSTANCE;
        }

        public static QuestFileFixture nullInstance() {
            return null;
        }

        public Object getOrCreateTeamData() {
            return TEAM_DATA;
        }
    }
}
