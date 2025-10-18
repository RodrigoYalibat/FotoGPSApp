package com.example.fotogpsapp

import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var ivFoto: ImageView
    private lateinit var btnTomarFoto: Button
    private lateinit var btnUbicacion: Button
    private lateinit var btnVerMaps: Button
    private lateinit var btnLimpiar: Button
    private lateinit var tvCoordenadas: TextView
    private lateinit var tvDireccion: TextView
    private lateinit var tvEstado: TextView

    private var lat: Double? = null
    private var lon: Double? = null

    // === Cámara: preview rápida en Bitmap ===
    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            ivFoto.setImageBitmap(bmp)
            setEstado("Foto capturada.")
        } else {
            setEstado("No se tomó la foto.")
        }
    }

    // === Lanzador de permisos múltiples (cámara + ubicación) ===
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val camOK = result[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA)
        val locOK = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        setEstado("Permisos — Cámara: ${if (camOK) "OK" else "NO"}, Ubicación: ${if (locOK) "OK" else "NO"}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivFoto = findViewById(R.id.ivFoto)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnUbicacion = findViewById(R.id.btnUbicacion)
        btnVerMaps = findViewById(R.id.btnVerMaps)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        tvCoordenadas = findViewById(R.id.tvCoordenadas)
        tvDireccion = findViewById(R.id.tvDireccion)
        tvEstado = findViewById(R.id.tvEstado)

        // Pedimos permisos al arrancar (también podrías pedirlos on-demand)
        ensurePermissions(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )) {}

        btnTomarFoto.setOnClickListener { tomarFoto() }
        btnUbicacion.setOnClickListener { obtenerUbicacionSegura() }
        btnVerMaps.setOnClickListener { abrirMaps() }
        btnLimpiar.setOnClickListener { limpiarUI() }
    }

    // ===== Helpers de permisos =====
    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions(perms: Array<String>, onGranted: () -> Unit) {
        val all = perms.all { hasPermission(it) }
        if (all) {
            onGranted()
            return
        }
        val needsRationale = perms.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
        if (needsRationale) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permisos necesarios")
                .setMessage("La app requiere acceso a la Cámara y Ubicación para continuar.")
                .setPositiveButton("Conceder") { _, _ -> requestPerms.launch(perms) }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            requestPerms.launch(perms)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // ===== Acciones =====
    private fun tomarFoto() {
        ensurePermissions(arrayOf(Manifest.permission.CAMERA)) {
            try {
                takePicturePreview.launch(null)
            } catch (se: SecurityException) {
                setEstado("Permiso de cámara denegado.")
            }
        }
    }

    @SuppressLint("MissingPermission") // ya verificamos permisos manualmente
    private fun obtenerUbicacionSegura() {
        val req = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        ensurePermissions(req) {
            val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!fine && !coarse) {
                setEstado("Sin permisos de ubicación. Abre Ajustes.")
                openAppSettings()
                return@ensurePermissions
            }
            try {
                val fused = LocationServices.getFusedLocationProviderClient(this)
                fused.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            lat = loc.latitude
                            lon = loc.longitude
                            tvCoordenadas.text = "Lat: ${"%.5f".format(lat)}  Lon: ${"%.5f".format(lon)}"
                            setEstado("Ubicación obtenida.")
                            // Intentar Geocoder (requiere red)
                            cargarDireccion()
                        } else {
                            setEstado("Sin ubicación previa. Activa GPS / cielo abierto.")
                        }
                    }
                    .addOnFailureListener { e ->
                        setEstado("Error al obtener ubicación: ${e.message}")
                    }
            } catch (se: SecurityException) {
                setEstado("Permiso de ubicación denegado.")
            }
        }
    }

    private fun cargarDireccion() {
        try {
            val la = lat ?: return
            val lo = lon ?: return
            val geocoder = Geocoder(this, Locale.getDefault())
            // getFromLocation está deprecado en 34+, pero sirve para demo
            val list = geocoder.getFromLocation(la, lo, 1)
            val addr = list?.firstOrNull()
            tvDireccion.text = if (addr != null) {
                "Dirección: ${addr.getAddressLine(0)}"
            } else "Dirección: (no disponible)"
        } catch (_: Exception) {
            tvDireccion.text = "Dirección: (error geocoder)"
        }
    }

    private fun abrirMaps() {
        if (lat != null && lon != null) {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Ubicación)")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            startActivity(mapIntent)
        } else {
            setEstado("Primero obtén la ubicación.")
        }
    }

    private fun limpiarUI() {
        ivFoto.setImageResource(0)
        lat = null; lon = null
        tvCoordenadas.text = "Lat: -, Lon: -"
        tvDireccion.text = "Dirección: -"
        setEstado("Campos limpiados.")
    }

    // ===== Utilidad UI =====
    private fun setEstado(msg: String) {
        tvEstado.text = "Estado: $msg"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
