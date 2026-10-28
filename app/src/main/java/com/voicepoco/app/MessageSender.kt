package com.voicepoco.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager

object MessageSender {
    data class Person(val number: String?, val label: String, val matches: Int)

    fun resolve(ctx: Context, name: String): Person {
        if (name.isBlank()) return Person(null, name, 0)
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return Person(null, name, 0)
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
        val exact = ArrayList<Pair<String, String>>()
        val fuzzy = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val label = c.getString(1) ?: name
                val num = c.getString(0) ?: continue
                val pair = label to num
                if (label.equals(name, ignoreCase = true)) exact.add(pair) else fuzzy.add(pair)
            }
        }
        val pool = (if (exact.isNotEmpty()) exact else fuzzy).filter { it.second.filter { ch -> ch.isDigit() }.length >= 6 }
        if (pool.isEmpty()) return fuzzy(ctx, name)
        val q = name.lowercase().trim()
        fun score(label: String): Int {
            val l = label.lowercase().trim()
            return when {
                l == q -> 4
                l.startsWith(q) -> 3
                l.split(" ", "-", "_").any { it == q } -> 2
                l.contains(q) -> 1
                else -> 0
            }
        }
        val ranked = pool.sortedWith(compareByDescending<Pair<String, String>> { score(it.first) }.thenBy { it.first.length })
        val top = ranked.first()
        val topScore = score(top.first)
        val rivals = ranked.filter { score(it.first) == topScore && it.second.filter { ch -> ch.isDigit() }.takeLast(10) != top.second.filter { ch -> ch.isDigit() }.takeLast(10) }
        val matches = if (topScore >= 3) 1 else rivals.size + 1
        return Person(top.second, top.first, matches)
    }

    private fun fuzzy(ctx: Context, name: String): Person {
        val q = name.lowercase().trim()
        if (q.length < 4) return Person(null, name, 0)
        val rows = ArrayList<Pair<String, String>>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext() && rows.size < 4000) {
                    val label = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    if (num.filter { it.isDigit() }.length >= 6) rows.add(label to num)
                }
            }
        }
        if (rows.isEmpty()) return Person(null, name, 0)
        val limit = if (q.length >= 8) 2 else 1
        val scored = rows.map { (label, num) ->
            val l = label.lowercase().trim()
            val d = when {
                l == q -> 0
                l.startsWith(q) || q.startsWith(l) -> 1
                else -> minOf(distance(q, l), l.split(" ", "-", "_").minOfOrNull { distance(q, it) } ?: 9)
            }
            Triple(label, num, d)
        }.filter { it.third <= limit }.sortedBy { it.third }
        if (scored.isEmpty()) return Person(null, name, 0)
        val top = scored.first()
        val rivals = scored.filter {
            it.third == top.third && it.second.filter { ch -> ch.isDigit() }.takeLast(10) != top.second.filter { ch -> ch.isDigit() }.takeLast(10)
        }
        return Person(top.second, top.first, if (top.third == 0 || rivals.isEmpty()) 1 else rivals.size + 1)
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun send(ctx: Context, contact: String, text: String, app: String, dry: Boolean): String {
        val p = resolve(ctx, contact)
        val who = p.label.ifBlank { contact }
        if (p.matches > 1) return "There are ${p.matches} contacts named $contact. Which one?"
        if (app.contains("whatsapp")) {
            val svc = AssistantService.instance ?: return "WhatsApp needs accessibility on."
            return svc.whatsappSend(who, text, dry)
        }
        if (text.isBlank()) return "What should I say to $who?"
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED && !p.number.isNullOrBlank()) {
            return runCatching {
                SmsManager.getDefault().sendTextMessage(p.number, null, text, null, null)
                "Message sent to $who"
            }.getOrElse { "SMS failed: ${it.message}" }
        }
        val i = Intent(Intent.ACTION_SENDTO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.data = Uri.parse("smsto:" + Uri.encode(p.number ?: ""))
        if (text.isNotBlank()) i.putExtra("sms_body", text)
        ctx.startActivity(i)
        return "SMS app open, tap send"
    }
}
