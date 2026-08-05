package com.rtsbuilding.rtsbuilding.common.persist;

public interface UiStateRepository {

    UiSnapshot.Global loadGlobal();

    void saveGlobal(UiSnapshot.Global global);

    void clear();
}
