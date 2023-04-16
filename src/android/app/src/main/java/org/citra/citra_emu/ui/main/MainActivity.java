package org.citra.citra_emu.ui.main;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.splashscreen.SplashScreen;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Collections;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.AppBarLayout;

import org.citra.citra_emu.NativeLibrary;
import org.citra.citra_emu.R;
import org.citra.citra_emu.activities.EmulationActivity;
import org.citra.citra_emu.activities.EmulationActivity2;
import org.citra.citra_emu.contracts.OpenFileResultContract;
import org.citra.citra_emu.features.settings.ui.SettingsActivity;
import org.citra.citra_emu.model.GameProvider;
import org.citra.citra_emu.ui.platform.PlatformGamesFragment;
import org.citra.citra_emu.utils.AddDirectoryHelper;
import org.citra.citra_emu.utils.BillingManager;
import org.citra.citra_emu.utils.CitraDirectoryHelper;
import org.citra.citra_emu.utils.DirectoryInitialization;
import org.citra.citra_emu.utils.FileBrowserHelper;
import org.citra.citra_emu.utils.InsetsHelper;
import org.citra.citra_emu.utils.PermissionsHandler;
import org.citra.citra_emu.utils.PicassoUtils;
import org.citra.citra_emu.utils.StartupHandler;
import org.citra.citra_emu.utils.ThemeUtil;

import com.lge.display.DisplayManagerHelper;
import com.lge.display.DisplayManagerHelper.SwivelStateCallback;

/**
 * The main Activity of the Lollipop style UI. Manages several PlatformGamesFragments, which
 * individually display a grid of available games for each Fragment, in a tabbed layout.
 */
public final class MainActivity extends AppCompatActivity implements MainView {
    private Toolbar mToolbar;
    private int mFrameLayoutId;
    private PlatformGamesFragment mPlatformGamesFragment;

    private MainPresenter mPresenter = new MainPresenter(this);

    // Singleton to manage user billing state
    private static BillingManager mBillingManager;

    private static MenuItem mPremiumButton;

    public static final String ACTION_FINISH_MAIN2ACTIVITY = "org.citra.citra_emu.ui.main.emu_activity2.finish";
    private DisplayManagerHelper mDisplayManagerHelper;
    private MySwivelStateCallback mSwivelStateCallback;

    private static final int TARGET_SUB_TYPE = DisplayManagerHelper.TYPE_SWIVEL;
    private static final String TAG = "SwivelSample";
    private static final int MAIN_SCREEN_ID = 0;
    private int mSubScreenId = MAIN_SCREEN_ID;
    private boolean mIsLgMultiDisplayDevice = false;
    private View mView;             // object to check where the view is attached


    private final CitraDirectoryHelper citraDirectoryHelper = new CitraDirectoryHelper(this, () -> {
        // If mPlatformGamesFragment is null means game directory have not been set yet.
        if (mPlatformGamesFragment == null) {
            mPlatformGamesFragment = new PlatformGamesFragment();
            getSupportFragmentManager()
                .beginTransaction()
                .add(mFrameLayoutId, mPlatformGamesFragment)
                .commit();
            showGameInstallDialog();
        }
    });

    private final ActivityResultLauncher<Uri> mOpenCitraDirectory =
        registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), result -> {
            if (result == null)
                return;
            citraDirectoryHelper.showCitraDirectoryDialog(result);
        });

    private final ActivityResultLauncher<Uri> mOpenGameListLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), result -> {
            if (result == null)
                return;
            int takeFlags =
                (Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(result, takeFlags);
            // When a new directory is picked, we currently will reset the existing games
            // database. This effectively means that only one game directory is supported.
            // TODO(bunnei): Consider fixing this in the future, or removing code for this.
            getContentResolver().insert(GameProvider.URI_RESET, null);
            // Add the new directory
            mPresenter.onDirectorySelected(result.toString());
        });

    private final ActivityResultLauncher<Boolean> mOpenFileLauncher =
        registerForActivityResult(new OpenFileResultContract(), result -> {
            if (result == null)
                return;
            String[] selectedFiles = FileBrowserHelper.getSelectedFiles(
                result, getApplicationContext(), Collections.singletonList("cia"));
            if (selectedFiles == null) {
                Toast
                    .makeText(getApplicationContext(), R.string.cia_file_not_found,
                              Toast.LENGTH_LONG)
                    .show();
                return;
            }
            NativeLibrary.InstallCIAS(selectedFiles);
            mPresenter.refreshGameList();
        });

    private Display getSecondaryDisplay() {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        for (Display display : displays) {
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return display;
            }
        }
        return null;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(
            ()
                -> (PermissionsHandler.hasWriteAccess(this) &&
                    !DirectoryInitialization.areCitraDirectoriesReady()));

        ThemeUtil.applyTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mView = findViewById(R.id.coordinator_main);

        if(hasLGMultiScreenFeature()) {
            mIsLgMultiDisplayDevice = DisplayManagerHelper.isMultiDisplayDevice();
        }

        if (mIsLgMultiDisplayDevice) {
            mDisplayManagerHelper = new DisplayManagerHelper(this);
            mSubScreenId = mDisplayManagerHelper.getMultiDisplayId();

            if (DisplayManagerHelper.getMultiDisplayType() == TARGET_SUB_TYPE) {
                Log.i(TAG, "This is swivel type.");
            } else {
                Log.i(TAG, "This is not swivel type.");
            }
        }



        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        findViews();

        setSupportActionBar(mToolbar);

        mFrameLayoutId = R.id.games_platform_frame;
        mPresenter.onCreate();

        if (savedInstanceState == null) {
            StartupHandler.HandleInit(this, mOpenCitraDirectory);
            if (PermissionsHandler.hasWriteAccess(this)) {
                mPlatformGamesFragment = new PlatformGamesFragment();
                getSupportFragmentManager().beginTransaction().add(mFrameLayoutId, mPlatformGamesFragment)
                        .commit();
            }
        } else {
            mPlatformGamesFragment = (PlatformGamesFragment) getSupportFragmentManager().getFragment(savedInstanceState, "mPlatformGamesFragment");
        }
        PicassoUtils.init();

        // Setup billing manager, so we can globally query for Premium status
        mBillingManager = new BillingManager(this);

        // Dismiss previous notifications (should not happen unless a crash occurred)
        EmulationActivity.tryDismissRunningNotification(this);

        setInsets();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mIsLgMultiDisplayDevice) {
            if (mSwivelStateCallback == null) {
                mSwivelStateCallback = new MySwivelStateCallback();
                mDisplayManagerHelper.registerSwivelStateCallback(mSwivelStateCallback);
            }

            // Register a callback to be invoked when the global layout state or the visibility of views within the view tree changes.
            mView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (mDisplayManagerHelper.getSwivelState() == DisplayManagerHelper.SWIVEL_SWIVELED) {
                        startLaunchActivity();
                    }
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (PermissionsHandler.hasWriteAccess(this)) {
            if (getSupportFragmentManager() == null) {
                return;
            }
            if (outState == null) {
                return;
            }
            getSupportFragmentManager().putFragment(outState, "mPlatformGamesFragment", mPlatformGamesFragment);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.addDirIfNeeded(new AddDirectoryHelper(this));

        ThemeUtil.setSystemBarMode(this, ThemeUtil.getIsLightMode(getResources()));
    }

    // TODO: Replace with a ButterKnife injection.
    private void findViews() {
        mToolbar = findViewById(R.id.toolbar_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_game_grid, menu);
        mPremiumButton = menu.findItem(R.id.button_premium);

        if (mBillingManager.isPremiumCached()) {
            // User had premium in a previous session, hide upsell option
            setPremiumButtonVisible(false);
        }

        return true;
    }

    static public void setPremiumButtonVisible(boolean isVisible) {
        if (mPremiumButton != null) {
            mPremiumButton.setVisible(isVisible);
        }
    }

    /**
     * MainView
     */

    @Override
    public void setVersionString(String version) {
        mToolbar.setSubtitle(version);
    }

    @Override
    public void refresh() {
        getContentResolver().insert(GameProvider.URI_REFRESH, null);
        refreshFragment();
    }

    @Override
    public void launchSettingsActivity(String menuTag) {
        if (PermissionsHandler.hasWriteAccess(this)) {
            SettingsActivity.launch(this, menuTag, "");
        } else {
            PermissionsHandler.checkWritePermission(this, mOpenCitraDirectory);
        }
    }

    @Override
    public void launchFileListActivity(int request) {
        if (PermissionsHandler.hasWriteAccess(this)) {
            switch (request) {
                case MainPresenter.REQUEST_SELECT_CITRA_DIRECTORY:
                mOpenCitraDirectory.launch(null);
                break;
                case MainPresenter.REQUEST_ADD_DIRECTORY:
                mOpenGameListLauncher.launch(null);
                break;
                case MainPresenter.REQUEST_INSTALL_CIA:
                mOpenFileLauncher.launch(true);
                break;
            }
        } else {
            PermissionsHandler.checkWritePermission(this, mOpenCitraDirectory);
        }
    }

    /**
     * Called by the framework whenever any actionbar/toolbar icon is clicked.
     *
     * @param item The icon that was clicked on.
     * @return True if the event was handled, false to bubble it up to the OS.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mPresenter.handleOptionSelection(item.getItemId());
    }

    private void refreshFragment() {
        if (mPlatformGamesFragment != null) {
            mPlatformGamesFragment.refresh();
        }
    }

    private void showGameInstallDialog() {
        new MaterialAlertDialogBuilder(this)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(R.string.app_name)
            .setMessage(R.string.app_game_install_description)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok,
                               (d, v) -> mOpenGameListLauncher.launch(null))
            .show();
    }

    @Override
    protected void onDestroy() {
        EmulationActivity.tryDismissRunningNotification(this);
        super.onDestroy();
    }

    /**
     * @return true if Premium subscription is currently active
     */
    public static boolean isPremiumActive() {
        return mBillingManager.isPremiumActive();
    }

    /**
     * Invokes the billing flow for Premium
     *
     * @param callback Optional callback, called once, on completion of billing
     */
    public static void invokePremiumBilling(Runnable callback) {
        mBillingManager.invokePremiumBilling(callback);
    }

    private void setInsets() {
        AppBarLayout appBar = findViewById(R.id.appbar);
        FrameLayout frame = findViewById(R.id.games_platform_frame);
        ViewCompat.setOnApplyWindowInsetsListener(frame, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            InsetsHelper.insetAppBar(insets, appBar);
            frame.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });
    }



    private void startLaunchActivity() {
        startNewActivity(new Intent(this, EmulationActivity2.class));
    }


    private void startNewActivity(Intent intent) {
        ActivityOptions options = ActivityOptions.makeBasic();
        // set Display ID where your activity will be launched
        int launchDisplayId = MAIN_SCREEN_ID;
        if(mIsLgMultiDisplayDevice &&
                mView.getDisplay().getDisplayId() == MAIN_SCREEN_ID &&
                mDisplayManagerHelper.getSwivelState() == DisplayManagerHelper.SWIVEL_SWIVELED) {
            launchDisplayId = mSubScreenId;
        }
        options.setLaunchDisplayId(launchDisplayId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent, options.toBundle());
    }

    private void stopMain2Activity() {
        Intent intent = new Intent(ACTION_FINISH_MAIN2ACTIVITY);
        sendBroadcast(intent);
    }

    private boolean hasLGMultiScreenFeature(){
        String feature = "com.lge.multiscreen";
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(feature);
    }

    /**
     * Updates the swivel state based on the callback actions.
     *
     * @param state The state of swivel, e.g. SWIVEL_START, SWIVEL_END, etc.
     */
    private class MySwivelStateCallback extends SwivelStateCallback {
        @Override
        public void onSwivelStateChanged(int state) {
            switch (state) {
                case DisplayManagerHelper.SWIVEL_START:
                    // Swivel start
                    break;
                case DisplayManagerHelper.SWIVEL_END:
                    // Swivel complete
                    startLaunchActivity();
                    break;
                case DisplayManagerHelper.NON_SWIVEL_START:
                    // Non Swivel start
                    break;
                case DisplayManagerHelper.NON_SWIVEL_END:
                    // Non Swivel complete
                    stopMain2Activity();
                    break;
                default:
                    // default value
                    break;
            }
        }
    }
}
