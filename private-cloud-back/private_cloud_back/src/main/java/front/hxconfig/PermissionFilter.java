package front.hxconfig;

import java.util.List;

public abstract class PermissionFilter<T> {

    public List<T> filterByPermission(List<T> items, Long userId, String permission) {
        if (isRbacEnabled()) {
            return applyRbacFilter(items, userId, permission);
        }
        return items;
    }

    protected abstract boolean isRbacEnabled();

    protected abstract List<T> applyRbacFilter(List<T> items, Long userId, String permission);
}
