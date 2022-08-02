package com.example.wiberdriver.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.wiberdriver.R
import com.example.wiberdriver.databinding.ActivityHomeBinding
import com.example.wiberdriver.models.entity.CarRequest
import com.example.wiberdriver.models.enums.CarRequestStatus
import com.example.wiberdriver.utils.Const
import com.example.wiberdriver.utils.Const.TAG
import com.example.wiberdriver.utils.StompUtils
import com.example.wiberdriver.viewmodels.HomeViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.dto.StompCommand
import ua.naiksoftware.stomp.dto.StompHeader
import ua.naiksoftware.stomp.dto.StompMessage
import java.util.*


class HomeActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var startLocation: LatLng

    // bottom sheet
    private lateinit var bottomLayout :LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        bottomLayout = findViewById(R.id.bottom_sheet_layout)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomLayout)
        val fromLayout = bottomLayout.findViewById<TextInputLayout>(R.id.fromInputLayout)
        val toWhereLayout = bottomLayout.findViewById<TextInputLayout>(R.id.toWhereInputLayout)
        val distanceLayout = bottomLayout.findViewById<TextInputLayout>(R.id.distanceToGo)
        val moneyLayout = bottomLayout.findViewById<TextInputLayout>(R.id.moneyToPay)
        val acceptRequestBtn = bottomLayout.findViewById<Button>(R.id.accept_button)
        val rejectRequestBtn = bottomLayout.findViewById<Button>(R.id.reject_button)

        var toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Home"
        var drawerLayout = binding.drawerLayout
        var navigationView = binding.navView
        var actionBarDrawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.openNavDrawer,
            R.string.closeNavDrawer
        )
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_payment_method -> {
                    val intent = Intent(this, PaymentMethodActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        //map
        val supportMapFragment: SupportMapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        // async map
        Log.i("info", "async map")
        supportMapFragment.getMapAsync(this)

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        // socket config
        val stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, Const.address)
        StompUtils.lifecycle(stompClient)
        Toast.makeText(this, "Start connecting to server", Toast.LENGTH_SHORT).show()
        // Connect to WebSocket server
        stompClient.connect()

        // 订阅消息
        Log.i(Const.TAG, "Subscribe broadcast endpoint to receive response")
        stompClient.topic(Const.broadcastResponse).subscribe { stompMessage: StompMessage ->
            val jsonObject = JSONObject(stompMessage.payload)
            Log.i(Const.TAG, "Receive: " + stompMessage.payload)
            runOnUiThread {
                try {
                    val latCustomer = jsonObject.getDouble("latPickingAddress")
                    val lngCustomer = jsonObject.getDouble("lngPickingAddress")
                    if (latCustomer != null && lngCustomer != null) {
                        val results = FloatArray(1)
                        startLocation = LatLng(latCustomer, lngCustomer)
                        Location.distanceBetween(
                            startLocation.latitude, startLocation.longitude,
                            latCustomer, lngCustomer, results
                        )
                        val distance = results[0]
                        if (distance < 5000.0) {
                            if (!this.isFinishing) {
                                if(bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED){
                                    fromLayout.editText?.setText("Not yet")
                                    toWhereLayout.editText?.setText("Not yet")
                                    distanceLayout.editText?.setText("Not yet")
                                    moneyLayout.editText?.setText("Not yet")
                                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                                }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
        rejectRequestBtn.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        acceptRequestBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Accept Request")
                .setMessage("Are you sure to accept this request?")
                .setPositiveButton("OK") { dialog, which ->
                    // send rest api to server that accept the car request
                    dialog.dismiss()
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
                .setNegativeButton("Cancel") { dialog, which ->
                    dialog.dismiss()
                }
                .show()
        }
        binding.testSocketButton.setOnClickListener { v ->
            var jsonObject: String? = null
            val testCarRequest = CarRequest(
                "ID req 1234", "62e1dc2eb7b9de371343194e", "0123456789", "Khoa hoc tu nhien",
                "Benh vien tu du", 106.6832, 10.7685,
                107.2153, 10.666, CarRequestStatus.WAITING.status
            )
            try {
                jsonObject = Gson().toJson(testCarRequest)
            } catch (e: JSONException) {
                Log.d(TAG, "JSONException: " + e.message)
            }
            stompClient.send(
                StompMessage( // Stomp command
                    StompCommand.SEND,  // Stomp Headers, Send Headers with STOMP
                    // the first header is required, and the other can be customized by ourselves
                    Arrays.asList(
                        StompHeader(StompHeader.DESTINATION, Const.broadcast),
                        StompHeader(
                            "authorization",
                            "this is a token generated by your code!"
                        )
                    ),  // Stomp payload
                    jsonObject.toString()
                )
            ).subscribe()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Add a marker at current user location and move the camera
        mMap.uiSettings.isZoomControlsEnabled = false
        setUpMap()

    }

    private fun setUpMap() {
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.i("info", "permission denied")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            Log.i("info", "permission granted")
            mMap.isMyLocationEnabled = true
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    startLocation = LatLng(location.latitude, location.longitude)
                    Log.i("info", "current location: $currentLatLng")
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    mMap.addMarker(
                        com.google.android.gms.maps.model.MarkerOptions()
                            .position(currentLatLng)
                            .title("You are here")
                    )
                }
            }
        }
    }
}