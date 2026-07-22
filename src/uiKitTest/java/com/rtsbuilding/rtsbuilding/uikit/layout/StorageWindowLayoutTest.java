package com.rtsbuilding.rtsbuilding.uikit.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
final class StorageWindowLayoutTest{
 @Test void preservesProductionColumns(){int w=StorageWindowLayout.rowWidth(372,true);assertEquals(361,w);assertEquals(315,StorageWindowLayout.unlinkX(8,w));assertEquals(271,StorageWindowLayout.extractX(8,w));assertEquals(219,StorageWindowLayout.priorityX(8,w));}
 @Test void visibleRowsStayBounded(){assertEquals(4,StorageWindowLayout.visibleRows(174));}
}
