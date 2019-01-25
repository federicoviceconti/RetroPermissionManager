# RetroPermissionManager
The easy way to handle permissions (for Android 4.4 and higher devices)

## How to use ##
Permission manager are composed by two methods.

###### requestPermission(config: ConfigParameter, onSuccessListener: OnSuccessListener, onFailureListener: OnFailureListener, onNeverAskAgainListener: OnNeverAskAgainListener, allPermissionRecap: AllPermissionRecap?) ######
The first is requestPermission. This one require several parameters:
- config: where we have to specify the list of permissions and the current activity
- onSuccessListener: called when permission is grant
- onFailureListener: called when permission is
- onNeverAskAgainListener: called when we check never ask
- allPermissionRecap: called at the end and this one is not null

**Code example**
*Kotlin:*
```
val config = PermissionManager.ConfigParameter(
                arrayListOf(PermissionType.CAMERA, PermissionType.CALL_PHONE), this)

        PermissionManager.requestPermission(config,
                object : PermissionManager.OnSuccessListener {
                    override fun onOperationCompleted(type: PermissionType) {
                        Log.d("permission", "complete: $type")
                    }
                },
                object: PermissionManager.OnFailureListener {
                    override fun onOperationCompleted(type: PermissionType) {
                        Log.d("permission", "fail: $type")
                    }
                },
                object: PermissionManager.OnNeverAskAgainListener {
                    override fun onOperationCompleted(type: PermissionType) {
                        Log.d("permission", "never ask: $type")
                    }
                },
                object: PermissionManager.AllPermissionRecap {
                    override fun onAllOperationCompleted(listPermissions: List<Pair<PermissionType, Boolean>>) {
                        Log.d("permission", "all completed: $listPermissions")
                    }
                })
```

*Java*:
```
ArrayList<PermissionType> al = new ArrayList<>();
        al.add(PermissionType.CAMERA);
        al.add(PermissionType.CALL_PHONE);

        PermissionManager.ConfigParameter config = new PermissionManager.ConfigParameter(al, this);
        PermissionManager.INSTANCE.requestPermission(config, type -> {
            Log.d("permission", "completed");
        }, type -> {
            Log.d("permission", "denied");
        }, type -> {
            Log.d("permission", "never ask");
        }, null);
```

###### onRequestPermissionsResult(permissionResult: PermissionResult) ######
The second method is MANDATORY into your activity. You must be call into **onRequestPermissionsResult**'s callback

**Code example**

*Kotlin:*
```
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionManager.onRequestPermissionsResult(PermissionManager.PermissionResult(requestCode, permissions, grantResults))
        }
```
*Java:*
```
@Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.INSTANCE.onRequestPermissionsResult(new PermissionManager.PermissionResult(requestCode, permissions, grantResults));
    }
```
