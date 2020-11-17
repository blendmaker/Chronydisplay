package at.blendmaker.chrony;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.androidannotations.annotations.AfterExtras;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.SeekBarProgressChange;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {
    private final static double mToFeet = 3.28084;
    private final static String REQUEST_PERMISSION_ACTION =
            "at.blendmaker.chrony.REQUEST_PERMISSION_ACTION";

    @ViewById(R.id.txt_val_weight)
    TextView txtWeight;

    @ViewById(R.id.list)
    TableLayout list;

    @SystemService()
    UsbManager usbManager;

    @Extra(UsbManager.EXTRA_DEVICE)
    UsbDevice usbDevice;
    @Extra(UsbManager.EXTRA_PERMISSION_GRANTED)
    boolean usbPermissionGranted = false;

    @SuppressLint("DefaultLocale")
    @SeekBarProgressChange(R.id.weight)
    void updateWeight(SeekBar seekBar, int progress) {
        txtWeight.setText( String.format( "%.2f", (progress * 0.01f) ) );
    }

    @AfterExtras
    void connectUSB() {
        if (!getIntent().getAction().equals(REQUEST_PERMISSION_ACTION) &&
                !getIntent().getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            Log.i(this.getClass().getCanonicalName(), "Activity startet without device");
            return;
        }

        if (!usbPermissionGranted) {
            usbManager.requestPermission(usbDevice, PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(REQUEST_PERMISSION_ACTION),
                    0));
        }

        if (usbDevice == null) {
            Log.i(this.getClass().getCanonicalName(), "No device extra found");
            return;
        }

        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.i(this.getClass().getCanonicalName(), "No available driver found, probing cdc driver");
            ProbeTable customTable = new ProbeTable();
            customTable.addProduct(6991, 37382, CdcAcmSerialDriver.class); // Sparkfun's Pro Micro
            availableDrivers = new UsbSerialProber(customTable).findAllDrivers(usbManager);

            if (availableDrivers.isEmpty()) {
                Log.i(this.getClass().getCanonicalName(), "Still none found, exiting");
                return;
            }
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
            Log.i(this.getClass().getCanonicalName(), "Permissions will be requested");
            usbManager.requestPermission(usbDevice, PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(REQUEST_PERMISSION_ACTION),
                    0));
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port.open(connection);
            if (port.getDriver() instanceof CdcAcmSerialDriver) {
                port.setDTR(true);
            }
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) { e.printStackTrace(); return; }
        SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
        Executors.newSingleThreadExecutor().submit(usbIoManager);
        Log.i(this.getClass().getCanonicalName(), "UsbConnection established");
    }

    @Override
    public void onNewData(byte[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            updateInput( new String(data, StandardCharsets.UTF_8) );
        } else {
            updateInput( new String(data, Charset.forName("UTF-8")) );
        }
    }

    @Override
    public void onRunError(Exception e) {

    }

    @SuppressLint("DefaultLocale")
    @UiThread
    void updateInput(String s) {
        if (s.isEmpty()) { return; } // omit faulty values
        float mps = Float.parseFloat(s);
        float weight = Float.parseFloat(txtWeight.getText().toString());

        if (mps <= 0 || weight <= 0) { return; } // omit faulty values

        TableRow row =
                (TableRow) getLayoutInflater().inflate(R.layout.list_item, list, false);
        ((TextView) row.findViewById(R.id.txt_val_ms))
                .setText(String.format("%.2f", mps ));
        ((TextView) row.findViewById(R.id.txt_val_fps))
                .setText(String.format("%.2f", (mps*mToFeet) ));
        ((TextView) row.findViewById(R.id.txt_val_joule))
                .setText(String.format("%.2f", calcEnergy(weight, mps)));
        list.addView(row, 1);
    }

    @Click(R.id.btn_clear)
    void clear() {
        while (list.getChildCount() > 1) {
            list.removeViewAt(1);
        }
    }

    private static float calcEnergy(float weight, float speed) {
        return 0.5f*(weight/1000)*(speed*speed);
    }
}