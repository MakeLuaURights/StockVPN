package com.stockvpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stockvpn.app.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            val key = binding.etKey.text.toString().trim()
            if (key.startsWith("vmess://")) {
                val config = parseVmess(key)
                if (config != null) {
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        startVpn(config)
                    }
                } else {
                    Toast.makeText(this, "Ошибка разбора ключа", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Поддерживаются только vmess:// ключи", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            stopService(Intent(this, com.v2ray.ang.V2rayVPNService::class.java))
            binding.tvStatus.text = "Отключено"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val key = binding.etKey.text.toString().trim()
            val config = parseVmess(key)
            if (config != null) startVpn(config)
        }
    }

    private fun startVpn(config: String) {
        val intent = Intent(this, com.v2ray.ang.V2rayVPNService::class.java)
        intent.putExtra("CONFIG", config)
        startService(intent)
        binding.tvStatus.text = "Подключение..."
    }

    private fun parseVmess(link: String): String? {
        return try {
            val base64 = link.removePrefix("vmess://")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val vmess = JSONObject(String(bytes))

            val address = vmess.getString("add")
            val port = vmess.getInt("port")
            val id = vmess.getString("id")
            val aid = vmess.optInt("aid", 0)
            val net = vmess.optString("net", "tcp")
            val type = vmess.optString("type", "none")
            val host = vmess.optString("host", "")
            val path = vmess.optString("path", "/")
            val tls = vmess.optString("tls", "none")

            val config = JSONObject()
            config.put("inbounds", JSONArray().put(JSONObject().apply {
                put("listen", "127.0.0.1")
                put("port", 10808)
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
            }))
            config.put("outbounds", JSONArray().put(JSONObject().apply {
                put("protocol", "vmess")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", address)
                        put("port", port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", id)
                            put("alterId", aid)
                            put("security", "auto")
                        }))
                    }))
                })
                put("streamSettings", JSONObject().apply {
                    put("network", net)
                    put("security", tls)
                    if (net == "ws") {
                        put("wsSettings", JSONObject().apply {
                            put("path", path)
                            if (host.isNotEmpty()) {
                                put("headers", JSONObject().apply {
                                    put("Host", host)
                                })
                            }
                        })
                    }
                    if (tls == "tls") {
                        put("tlsSettings", JSONObject().apply {
                            put("serverName", host)
                        })
                    }
                })
            }))
            config.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val VPN_REQUEST_CODE = 123
    }
}
