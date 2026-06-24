package com.rtsbuilding.rtsbuilding.client.kernel;

/**
 * Feature Module 激活梯度。<p>
 * 每个模块根据当前是否需要运行自动升降级，空闲时降为 IDLE/OFF 以减少开销。
 *
 * <table>
 *   <tr><th>级别</th><th>tick()</th><th>render()</th><th>内存占用</th></tr>
 *   <tr><td>OFF</td><td>不调用</td><td>不调用</td><td>可回收</td></tr>
 *   <tr><td>IDLE</td><td>极简检查</td><td>不调用</td><td>常驻</td></tr>
 *   <tr><td>WARM</td><td>全量</td><td>条件渲染</td><td>全量</td></tr>
 *   <tr><td>HOT</td><td>全量</td><td>全量渲染</td><td>全量</td></tr>
 * </table>
 */
public enum ModuleState {
    OFF,
    IDLE,
    WARM,
    HOT
}
