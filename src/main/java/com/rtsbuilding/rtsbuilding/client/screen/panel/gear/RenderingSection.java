package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;

/**
 * 渲染设置折叠分区——在设置面板中管理"渲染设置"分区的渲染和交互。
 *
 * <p>TODO: 规划中的渲染设置条目如下：</p>
 * <ul>
 *   <li>渲染距离（滑块调节）</li>
 *   <li>粒子效果开关 / 密度</li>
 *   <li>信标光束开关</li>
 *   <li>方块轮廓开关</li>
 *   <li>阴影/光照质量</li>
 * </ul>
 *
 * <p>当前分区无内容行，默认折叠以节省空间。
 * 待渲染设置条目确定后，在 {@link #getContentLines()} 中返回对应行文本即可。
 * 如需交互控件（如滑块、开关），可重写 {@link #renderContent} 和 {@link #onContentLineClick}。</p>
 */
public class RenderingSection extends SettingsSection {

    public RenderingSection() {
        super("screen.rtsbuilding.settings.category.rendering");
        setExpanded(false); // 默认折叠
    }

    @Override
    protected String[] getContentLines() {
        return new String[] {
        };
    }
}
