package com.sobdosuddhi.app.domain

import com.sobdosuddhi.app.model.AiDetectionLabel
import com.sobdosuddhi.app.model.AiDetectionReport
import com.sobdosuddhi.app.model.ConfidenceLevel
import com.sobdosuddhi.app.model.LinguisticDimensionScore
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.ParagraphAiNote
import com.sobdosuddhi.app.model.ProofreadingIssue
import com.sobdosuddhi.app.model.RefinementChange
import com.sobdosuddhi.app.model.RefinementMode
import com.sobdosuddhi.app.model.RefinementReport
import com.sobdosuddhi.app.model.RepeatedSegment
import com.sobdosuddhi.app.model.SuspiciousSentence
import com.sobdosuddhi.app.model.TocEntry
import kotlin.math.sqrt

/**
 * Lightweight, zero-latency on-device Bengali linguistic analyzer.
 * Handles common spelling/punctuation/spacing errors, heading detection,
 * Table of Contents extraction, and deterministic duplicate paragraph/sentence detection
 * without consuming unnecessary AI requests.
 */
object BengaliLocalAnalyzer {

  private data class SpellingRule(
    val wrong: String,
    val right: String,
    val category: String,
    val explanation: String
  )

  private val commonBengaliRules = listOf(
    SpellingRule("শুধু মাত্র", "শুধুমাত্র", "বানান ভুল", "বাংলা প্রমিত বানানে ‘শুধুমাত্র’ একসাথে লেখা হয়।"),
    SpellingRule("ইতিমধ্যে", "ইতোমধ্যে", "বানান ভুল", "বাংলা একাডেমির প্রমিত বানান অনুযায়ী ‘ইতোমধ্যে’ শুদ্ধ রূপ।"),
    SpellingRule("দুরত্ব", "দূরত্ব", "কার ও ফলা", "‘দূর’ শব্দে দীর্ঘ ঊ-কার বসে, তাই ‘দূরত্ব’ শুদ্ধ।"),
    SpellingRule("শ্রাবন", "শ্রাবণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-ফলার পর ‘ণ’ বসে—তাই ‘শ্রাবণ’ শুদ্ধ।"),
    SpellingRule("নিরবতা", "নীরবতা", "কার ও ফলা", "‘নীরব’ শব্দে দীর্ঘ ঈ-কার বসে, তাই ‘নীরবতা’ শুদ্ধ।"),
    SpellingRule("সমিচিন", "সমীচীন", "কার ও ফলা", "‘সমীচীন’ শব্দে দুটি দীর্ঘ ঈ-কার ব্যবহৃত হয়।"),
    SpellingRule("পিপিলিকা", "পিপীলিকা", "কার ও ফলা", "প্রমিত বানান ‘পিপীলিকা’ (দ্বিতীয় অক্ষরে দীর্ঘ ঈ-কার)।"),
    SpellingRule("বাল্মিকি", "বাল্মীকি", "কার ও ফলা", "প্রমিত বানান ‘বাল্মীকি’।"),
    SpellingRule("সন্ন্যাসি", "সন্ন্যাসী", "কার ও ফলা", "‘সন্ন্যাসী’ শব্দান্তে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("অদ্ভুতুড়ে", "অদ্ভুতুড়ে", "বানান ভুল", "শুদ্ধ রূপ।").takeIf { false },
    SpellingRule("ভূল", "ভুল", "কার ও ফলা", "‘ভুল’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("ভালবাসা", "ভালোবাসা", "বানান ভুল", "আধুনিক প্রমিত বাংলা বানানে ‘ভালোবাসা’ ও-কারসহ লেখা সমীচীন।"),
    SpellingRule("পুরুষ্কার", "পুরস্কার", "বানান ভুল", "‘পুরস্কার’ শব্দে দন্ত্য-স ও হ্রস্ব উ-কার বসে।"),
    SpellingRule("আবিস্কার", "আবিষ্কার", "যুক্তাক্ষরের ভুল", "ষত্ব বিধান অনুযায়ী ‘আবিষ্কার’ শব্দে মূর্ধন্য-ষ বসে।"),
    SpellingRule("পরিষ্কার", "পরিষ্কার", "যুক্তাক্ষরের ভুল", "").takeIf { false },
    SpellingRule("পরিস্কার", "পরিষ্কার", "যুক্তাক্ষরের ভুল", "ষত্ব বিধান অনুযায়ী ই-কারের পর ‘ষ্কার’ (পরিষ্কার) শুদ্ধ।"),
    SpellingRule("স্বরস্বতী", "সরস্বতী", "বানান ভুল", "সঠিক বানান ‘সরস্বতী’ (প্রথম অক্ষরে ব-ফলা নেই)।"),
    SpellingRule("শ্মশানঘাট", "শ্মশানঘাট", "বানান ভুল", "").takeIf { false },
    SpellingRule("শশান", "শ্মশান", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘শ্মশান’ (শ্ম যুক্তাক্ষরসহ)।"),
    SpellingRule("উপলক্ষ্য", "উপলক্ষ", "বানান ভুল", "‘কোনো কিছু উপলক্ষে’ অর্থে য-ফলা ছাড়া ‘উপলক্ষ’ শুদ্ধ।"),
    SpellingRule("আকাঙ্খা", "আকাঙ্ক্ষা", "যুক্তাক্ষরের ভুল", "‘আকাঙ্ক্ষা’ শব্দে ঙ্ক্ষ যুক্তাক্ষর বসে।"),
    SpellingRule("মূহুর্ত", "মুহূর্ত", "কার ও ফলা", "প্রথম অক্ষরে হ্রস্ব উ-কার ও দ্বিতীয় অক্ষরে দীর্ঘ ঊ-কার: ‘মুহূর্ত’।"),
    SpellingRule("মূহূর্ত", "মুহূর্ত", "কার ও ফলা", "প্রথম অক্ষরে হ্রস্ব উ-কার ও দ্বিতীয় অক্ষরে দীর্ঘ ঊ-কার: ‘মুহূর্ত’।"),
    SpellingRule("মুহুর্ত", "মুহূর্ত", "কার ও ফলা", "দ্বিতীয় অক্ষরে দীর্ঘ ঊ-কার বসে: ‘মুহূর্ত’।"),
    SpellingRule("ভৌগলিক", "ভৌগোলিক", "কার ও ফলা", "সঠিক প্রমিত বানান ‘ভৌগোলিক’।"),
    SpellingRule("শারিীরিক", "শারীরিক", "কার ও ফলা", "সঠিক প্রমিত বানান ‘শারীরিক’।"),
    SpellingRule("শারিরীক", "শারীরিক", "কার ও ফলা", "সঠিক প্রমিত বানান ‘শারীরিক’।"),
    SpellingRule("শারিরিক", "শারীরিক", "কার ও ফলা", "সঠিক প্রমিত বানান ‘শারীরিক’।"),
    SpellingRule("প্রান", "প্রাণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-ফলার পর ‘ণ’ বসে: ‘প্রাণ’।"),
    SpellingRule("ক্ষুন্ন", "ক্ষুণ্ণ", "যুক্তাক্ষরের ভুল", "ক্ষ-এর পর ‘ণ্ণ’ যুক্তাক্ষর বসে: ‘ক্ষুণ্ণ’।"),
    SpellingRule("দূর্গা", "দুর্গা", "কার ও ফলা", "‘দুর্গা’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("দূর্ঘটনা", "দুর্ঘটনা", "কার ও ফলা", "‘দুর্ঘটনা’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("দূর্ভাগ্য", "দুর্ভাগ্য", "কার ও ফলা", "‘দুর্ভাগ্য’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("অধ্যায়সমুহ", "অধ্যায়সমূহ", "কার ও ফলা", "‘সমূহ’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("ব্যার্থ", "ব্যর্থ", "কার ও ফলা", "‘ব্যর্থ’ শব্দে আ-কার বসে না।"),
    SpellingRule("ন্যুনতম", "ন্যূনতম", "কার ও ফলা", "‘ন্যূনতম’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("অতীথী", "অতিথি", "কার ও ফলা", "‘অতিথি’ শব্দে দুটি হ্রস্ব ই-কার বসে।"),
    SpellingRule("অতিথী", "অতিথি", "কার ও ফলা", "‘অতিথি’ শব্দে দুটি হ্রস্ব ই-কার বসে।"),
    SpellingRule("প্রতিযোগীতা", "প্রতিযোগিতা", "কার ও ফলা", "-তা প্রত্যয় যুক্ত হলে দীর্ঘ ঈ-কার হ্রস্ব ই-কার হয়: ‘প্রতিযোগিতা’।"),
    SpellingRule("সহযোগীতা", "সহযোগিতা", "কার ও ফলা", "-তা প্রত্যয় যুক্ত হলে ‘সহযোগিতা’ শুদ্ধ।"),
    SpellingRule("স্বাগতম্", "স্বাগতম", "বানান ভুল", "আধুনিক বাংলায় হসন্ত ছাড়া ‘স্বাগতম’ প্রচলিত।"),
    SpellingRule("সম্বর্ধনা", "সংবর্ধনা", "বানান ভুল", "বাংলা একাডেমির প্রমিত বানান অনুযায়ী ‘সংবর্ধনা’ শুদ্ধ।"),
    SpellingRule("কিংবদন্তী", "কিংবদন্তি", "কার ও ফলা", "প্রমিত বাংলা বানানে ‘কিংবদন্তি’ শব্দে হ্রস্ব ই-কার বসে।"),
    SpellingRule("জ্যোৎস্না", "জ্যোৎস্না", "বানান ভুল", "").takeIf { false },
    SpellingRule("জোৎস্না", "জ্যোৎস্না", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘জ্যোৎস্না’ (য-ফলাসহ)।"),
    SpellingRule("জোছনা", "জ্যোৎস্না", "বানান ভুল", "তৎসম প্রমিত রূপ ‘জ্যোৎস্না’ (কাব্যে ‘জোছনা’ ইচ্ছাকৃত হলে অপরিবর্তিত রাখুন)।"),
    SpellingRule("অভিষেক", "অভিষেক", "বানান ভুল", "").takeIf { false },
    SpellingRule("অভিশেক", "অভিষেক", "বানান ভুল", "ষত্ব বিধান অনুযায়ী ‘অভিষেক’ শব্দে মূর্ধন্য-ষ বসে।"),
    SpellingRule("পরীক্ষিত", "পরীক্ষিত", "কার ও ফলা", "").takeIf { false },
    SpellingRule("পরিক্ষা", "পরীক্ষা", "কার ও ফলা", "‘পরীক্ষা’ শব্দে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("সমীক্ষা", "সমীক্ষা", "কার ও ফলা", "").takeIf { false },
    SpellingRule("সমিক্ষা", "সমীক্ষা", "কার ও ফলা", "‘সমীক্ষা’ শব্দে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("দারিদ্রতা", "দারিদ্র্য", "ব্যাকরণগত ভুল", "প্রমিত বাংলায় ‘দারিদ্রতা’-র পরিবর্তে ‘দারিদ্র্য’ অথবা ‘দরিদ্রতা’ শুদ্ধ।"),
    SpellingRule("উৎকর্ষতা", "উৎকর্ষ", "ব্যাকরণগত ভুল", "‘উৎকর্ষ’ নিজেই বিশেষ্য; এর সাথে ‘-তা’ প্রত্যয় যুক্ত হয় না।"),
    SpellingRule("সৌজন্যতা", "সৌজন্য", "ব্যাকরণগত ভুল", "‘সৌজন্য’ শুদ্ধ রূপ; ‘সৌজন্যতা’ বাহুল্য দোষ।"),
    SpellingRule("একত্রিত", "একত্র", "ব্যাকরণগত ভুল", "প্রমিত বাংলায় ‘একত্রিত’-র পরিবর্তে ‘একত্র’ ব্যবহার শুদ্ধ।"),
    SpellingRule("স্বাক্ষরতা", "সাক্ষরতা", "শব্দের ভুল প্রয়োগ", "অক্ষরজ্ঞান অর্থে ‘সাক্ষরতা’ (ব-ফলা ছাড়া) শুদ্ধ; ‘স্বাক্ষর’ অর্থ দস্তখত।"),
    SpellingRule("অধীনস্থ", "অধীন", "ব্যাকরণগত ভুল", "‘অধীন’ নিজেই পূর্ণ অর্থবোধক; ‘-স্থ’ বাহুল্য।"),
    SpellingRule("দুরবস্থা", "দুরবস্থা", "বানান ভুল", "").takeIf { false },
    SpellingRule("দূরবস্থা", "দুরবস্থা", "কার ও ফলা", "‘দুঃ + অবস্থা = দুরবস্থা’—এখানে হ্রস্ব উ-কার বসে।"),
    SpellingRule("দূর্বিষহ", "দুর্বিষহ", "কার ও ফলা", "‘দুর্বিষহ’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("উচ্ছাস", "উচ্ছ্বাস", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘উচ্ছ্বাস’ (ব-ফলাসহ)।"),
    SpellingRule("আশীর্বাদ", "আশীর্বাদ", "কার ও ফলা", "").takeIf { false },
    SpellingRule("আশির্বাদ", "আশীর্বাদ", "কার ও ফলা", "‘আশীর্বাদ’ শব্দে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("আশিষ", "আশিস", "বানান ভুল", "বাংলা একাডেমির প্রমিত বানান ‘আশিস’।"),
    SpellingRule("ইতিপূর্বে", "ইতঃপূর্বে", "বানান ভুল", "সঠিক প্রমিত বানান ‘ইতঃপূর্বে’।"),
    SpellingRule("গনতন্ত্র", "গণতন্ত্র", "বানান ভুল", "‘গণ’ শব্দে মূর্ধন্য-ণ বসে, তাই ‘গণতন্ত্র’ শুদ্ধ।"),
    SpellingRule("বর্ননা", "বর্ণনা", "বানান ভুল", "রেফের পর ‘ণ’ বসে—সঠিক বানান ‘বর্ণনা’।"),
    SpellingRule("কর্ন", "কর্ণ", "বানান ভুল", "রেফের পর ‘ণ’ বসে—সঠিক বানান ‘কর্ণ’।"),
    SpellingRule("পূর্ন", "পূর্ণ", "বানান ভুল", "রেফের পর ‘ণ’ বসে—সঠিক বানান ‘পূর্ণ’।"),
    SpellingRule("ধরন", "ধরন", "বানান ভুল", "").takeIf { false },
    SpellingRule("ধরণ", "ধরন", "বানান ভুল", "বাংলা একাডেমির প্রমিত বানানে ‘ধরন’ (দন্ত্য-ন) শুদ্ধ।"),
    SpellingRule("জিনিষ", "জিনিস", "বানান ভুল", "প্রমিত বাংলা বানানে ‘জিনিস’ (দন্ত্য-স) শুদ্ধ।"),
    SpellingRule("পোষ্ট", "পোস্ট", "যুক্তাক্ষরের ভুল", "বিদেশি শব্দে ‘স্ট’ বসে—সঠিক বানান ‘পোস্ট’।"),
    SpellingRule("মাষ্টার", "মাস্টার", "যুক্তাক্ষরের ভুল", "বিদেশি শব্দে ‘স্ট’ বসে—সঠিক বানান ‘মাস্টার’।"),
    SpellingRule("ষ্টেশন", "স্টেশন", "যুক্তাক্ষরের ভুল", "বিদেশি শব্দে ‘স্ট’ বসে—সঠিক বানান ‘স্টেশন’।"),
    SpellingRule("ব্যাথা", "ব্যথা", "কার ও ফলা", "সঠিক বানান ‘ব্যথা’ (আ-কার ছাড়া)।"),
    SpellingRule("ব্যাবহার", "ব্যবহার", "কার ও ফলা", "সঠিক বানান ‘ব্যবহার’ (প্রথম অক্ষরে আ-কার বসে না)।"),
    SpellingRule("ব্যাবসা", "ব্যবসা", "কার ও ফলা", "সঠিক বানান ‘ব্যবসা’ (প্রথম অক্ষরে আ-কার বসে না)।"),
    SpellingRule("অন্যান্য", "অন্যান্য", "বানান ভুল", "").takeIf { false },
    SpellingRule("অন্যন্য", "অন্যান্য", "বানান ভুল", "সঠিক বানান ‘অন্যান্য’।"),
    SpellingRule("গীতাঞ্জলী", "গীতাঞ্জলি", "কার ও ফলা", "‘-অঞ্জলি’ যুক্ত শব্দে হ্রস্ব ই-কার বসে—‘গীতাঞ্জলি’।"),
    SpellingRule("শ্রদ্ধাঞ্জলী", "শ্রদ্ধাঞ্জলি", "কার ও ফলা", "‘-অঞ্জলি’ যুক্ত শব্দে হ্রস্ব ই-কার বসে—‘শ্রদ্ধাঞ্জলি’।"),
    SpellingRule("পুষ্পাঞ্জলী", "পুষ্পাঞ্জলি", "কার ও ফলা", "‘-অঞ্জলি’ যুক্ত শব্দে হ্রস্ব ই-কার বসে—‘পুষ্পাঞ্জলি’।"),
    SpellingRule("উপযোগীতা", "উপযোগিতা", "কার ও ফলা", "-তা প্রত্যয় যুক্ত হলে ‘উপযোগিতা’ (হ্রস্ব ই-কার) শুদ্ধ।"),
    SpellingRule("মন্ত্রীসভা", "মন্ত্রিসভা", "কার ও ফলা", "সমাসবদ্ধ পদে ‘মন্ত্রিসভা’ (হ্রস্ব ই-কার) শুদ্ধ।"),
    SpellingRule("প্রানীবিদ্যা", "প্রাণিবিদ্যা", "কার ও ফলা", "সমাসবদ্ধ পদে ‘প্রাণিবিদ্যা’ (হ্রস্ব ই-কার) শুদ্ধ।"),
    SpellingRule("দন্দ্ব", "দ্বন্দ্ব", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘দ্বন্দ্ব’ (দুটি ব-ফলাসহ)।"),
    SpellingRule("উর্ধ্ব", "ঊর্ধ্ব", "কার ও ফলা", "‘ঊর্ধ্ব’ শব্দে দীর্ঘ ঊ বসে।"),
    SpellingRule("সমুজ্জল", "সমুজ্জ্বল", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘সমুজ্জ্বল’।"),
    SpellingRule("উজ্জল", "উজ্জ্বল", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘উজ্জ্বল’ (ব-ফলাসহ)।"),
    SpellingRule("এক সাথে", "একসাথে", "বানান ভুল", "প্রমিত বাংলা বানানে ‘একসাথে’ একসাথে লেখা সমীচীন।"),
    SpellingRule("অনেক গুলো", "অনেকগুলো", "বানান ভুল", "নির্দেশক ‘-গুলো’ পূর্ববর্তী শব্দের সাথে যুক্ত হয়: ‘অনেকগুলো’।"),
    SpellingRule("সব গুলো", "সবগুলো", "বানান ভুল", "নির্দেশক ‘-গুলো’ পূর্ববর্তী শব্দের সাথে যুক্ত হয়: ‘সবগুলো’।"),
    SpellingRule("কিভাবে", "কীভাবে", "কার ও ফলা", "প্রশ্নবোধক ‘কীভাবে’ (কেমন করে) অর্থে দীর্ঘ ঈ-কার প্রমিত।"),
    SpellingRule("স্বরন", "স্মরণ", "যুক্তাক্ষরের ভুল", "‘মনে করা’ অর্থে সঠিক বানান ‘স্মরণ’ (স্ম যুক্তাক্ষর ও মূর্ধন্য-ণ)।"),
    SpellingRule("বিস্মরন", "বিস্মরণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী সঠিক বানান ‘বিস্মরণ’।"),
    SpellingRule("কারন", "কারণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-এর পর মূর্ধন্য-ণ বসে: ‘কারণ’।"),
    SpellingRule("ধারনা", "ধারণা", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-এর পর মূর্ধন্য-ণ বসে: ‘ধারণা’।"),
    SpellingRule("সাধারন", "সাধারণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-এর পর মূর্ধন্য-ণ বসে: ‘সাধারণ’।"),
    SpellingRule("উদাহরন", "উদাহরণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-এর পর মূর্ধন্য-ণ বসে: ‘উদাহরণ’।"),
    SpellingRule("বিবরন", "বিবরণ", "বানান ভুল", "ণত্ব বিধান অনুযায়ী র-এর পর মূর্ধন্য-ণ বসে: ‘বিবরণ’।"),
    SpellingRule("নির্মান", "নির্মাণ", "বানান ভুল", "রেফের পর মূর্ধন্য-ণ বসে: ‘নির্মাণ’।"),
    SpellingRule("প্রমান", "প্রমাণ", "বানান ভুল", "র-ফলার পর মূর্ধন্য-ণ বসে: ‘প্রমাণ’।"),
    SpellingRule("গ্রহন", "গ্রহণ", "বানান ভুল", "র-ফলার পর মূর্ধন্য-ণ বসে: ‘গ্রহণ’।"),
    SpellingRule("ভ্রমন", "ভ্রমণ", "বানান ভুল", "র-ফলার পর মূর্ধন্য-ণ বসে: ‘ভ্রমণ’।"),
    SpellingRule("রুপ", "রূপ", "কার ও ফলা", "‘রূপ’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("রুপালী", "রূপালী", "কার ও ফলা", "‘রূপালী’ (বা প্রমিত ‘রুপালি’) শব্দে বানান-সামঞ্জস্য বজায় রাখুন।"),
    SpellingRule("সুবর্ন", "সুবর্ণ", "বানান ভুল", "রেফের পর মূর্ধন্য-ণ বসে: ‘সুবর্ণ’।"),
    SpellingRule("অর্পন", "অর্পণ", "বানান ভুল", "রেফের পর মূর্ধন্য-ণ বসে: ‘অর্পণ’।"),
    SpellingRule("দর্পন", "দর্পণ", "বানান ভুল", "রেফের পর মূর্ধন্য-ণ বসে: ‘দর্পণ’।"),
    SpellingRule("বিসর্জন", "বিসর্জন", "বানান ভুল", "").takeIf { false },
    SpellingRule("বিষন্ন", "বিষণ্ণ", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘বিষণ্ণ’ (ণ্ণ যুক্তাক্ষরসহ)।"),
    SpellingRule("বিশন্ন", "বিষণ্ণ", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘বিষণ্ণ’ (মূর্ধন্য-ষ ও ণ্ণ যুক্তাক্ষরসহ)।"),
    SpellingRule("আকর্ষন", "আকর্ষণ", "বানান ভুল", "মূর্ধন্য-ষ-এর পর মূর্ধন্য-ণ বসে: ‘আকর্ষণ’।"),
    SpellingRule("পোষাক", "পোশাক", "বানান ভুল", "বাংলা একাডেমির প্রমিত বানানে বিদেশি শব্দ ‘পোশাক’ (তালব্য-শ) শুদ্ধ।"),
    SpellingRule("শহীদ", "শহিদ", "কার ও ফলা", "বাংলা একাডেমির আধুনিক প্রমিত বানানে অ-তৎসম ‘শহিদ’ (হ্রস্ব ই-কার) প্রচলিত।").takeIf { false },
    SpellingRule("দূর্যোগ", "দুর্যোগ", "কার ও ফলা", "‘দুর্যোগ’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("দূর্গম", "দুর্গম", "কার ও ফলা", "‘দুর্গম’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("দূর্লভ", "দুর্লভ", "কার ও ফলা", "‘দুর্লভ’ শব্দে হ্রস্ব উ-কার বসে।"),
    SpellingRule("সান্তনা", "সান্ত্বনা", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘সান্ত্বনা’ (ন্ত্ব যুক্তাক্ষরসহ)।"),
    SpellingRule("সাংঘাতিক", "সাংঘাতিক", "বানান ভুল", "").takeIf { false },
    SpellingRule("অধ্যয়ন", "অধ্যয়ন", "বানান ভুল", "").takeIf { false },
    SpellingRule("অধ্যায়ন", "অধ্যয়ন", "কার ও ফলা", "সঠিক প্রমিত বানান ‘অধ্যয়ন’ (আ-কার ছাড়া)।"),
    SpellingRule("সমীচিন", "সমীচীন", "কার ও ফলা", "‘সমীচীন’ শব্দে দুটি দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("ভবিষ্যৎবাণী", "ভবিষ্যদ্বাণী", "যুক্তাক্ষরের ভুল", "সন্ধিজাত প্রমিত রূপ ‘ভবিষ্যদ্বাণী’।"),
    SpellingRule("লক্ষন", "লক্ষণ", "বানান ভুল", "ক্ষ-এর পর মূর্ধন্য-ণ বসে: ‘লক্ষণ’।"),
    SpellingRule("সংরক্ষন", "সংরক্ষণ", "বানান ভুল", "ক্ষ-এর পর মূর্ধন্য-ণ বসে: ‘সংরক্ষণ’।"),
    SpellingRule("পর্যবেক্ষন", "পর্যবেক্ষণ", "বানান ভুল", "ক্ষ-এর পর মূর্ধন্য-ণ বসে: ‘পর্যবেক্ষণ’।"),
    SpellingRule("নিরীক্ষন", "নিরীক্ষণ", "বানান ভুল", "ক্ষ-এর পর মূর্ধন্য-ণ বসে: ‘নিরীক্ষণ’।"),
    SpellingRule("ব্যাবস্থা", "ব্যবস্থা", "কার ও ফলা", "সঠিক বানান ‘ব্যবস্থা’ (প্রথম অক্ষরে আ-কার বসে না)।"),
    SpellingRule("ব্যায়", "ব্যয়", "কার ও ফলা", "সঠিক বানান ‘ব্যয়’ (আ-কার ছাড়া)।"),
    SpellingRule("ব্যাথিত", "ব্যথিত", "কার ও ফলা", "সঠিক বানান ‘ব্যথিত’ (আ-কার ছাড়া)।"),
    SpellingRule("অত্যান্ত", "অত্যন্ত", "কার ও ফলা", "সঠিক বানান ‘অত্যন্ত’ (আ-কার ছাড়া)।"),
    SpellingRule("ভবিষৎ", "ভবিষ্যৎ", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘ভবিষ্যৎ’ (ষ্য যুক্তাক্ষরসহ)।"),
    SpellingRule("ভবিষ্যত", "ভবিষ্যৎ", "বানান ভুল", "প্রমিত বাংলা বানানে খণ্ড-ৎ বসে: ‘ভবিষ্যৎ’।"),
    SpellingRule("কতৃপক্ষ", "কর্তৃপক্ষ", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘কর্তৃপক্ষ’ (রেফ ও ঋ-কারসহ)।"),
    SpellingRule("পৃথিবি", "পৃথিবী", "কার ও ফলা", "‘পৃথিবী’ শব্দান্তে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("পৃথীবি", "পৃথিবী", "কার ও ফলা", "সঠিক বানান ‘পৃথিবী’ (থ-এ হ্রস্ব ই-কার ও ব-এ দীর্ঘ ঈ-কার)।"),
    SpellingRule("জিবন", "জীবন", "কার ও ফলা", "‘জীবন’ শব্দে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("রাত্রী", "রাত্রি", "কার ও ফলা", "প্রমিত বানান ‘রাত্রি’ (হ্রস্ব ই-কার)।"),
    SpellingRule("নিশ্বাস", "নিঃশ্বাস", "বানান ভুল", "বিসর্গসহ প্রমিত বানান ‘নিঃশ্বাস’।"),
    SpellingRule("সন্ধা", "সন্ধ্যা", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘সন্ধ্যা’ (ন্ধ্য যুক্তাক্ষরসহ)।"),
    SpellingRule("সুর্য", "সূর্য", "কার ও ফলা", "‘সূর্য’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("ব্রিষ্টি", "বৃষ্টি", "কার ও ফলা", "‘বৃষ্টি’ শব্দে ঋ-কার বসে, র-ফলা নয়।"),
    SpellingRule("স্রিষ্টি", "সৃষ্টি", "কার ও ফলা", "‘সৃষ্টি’ শব্দে ঋ-কার বসে, র-ফলা নয়।"),
    SpellingRule("দ্রিষ্টি", "দৃষ্টি", "কার ও ফলা", "‘দৃষ্টি’ শব্দে ঋ-কার বসে, র-ফলা নয়।"),
    SpellingRule("মিষ্ঠি", "মিষ্টি", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘মিষ্টি’ (ষ্ট যুক্তাক্ষরসহ)।"),
    SpellingRule("অবস্যই", "অবশ্যই", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘অবশ্যই’ (শ্য যুক্তাক্ষরসহ)।"),
    SpellingRule("মানুস", "মানুষ", "বানান ভুল", "সঠিক বানান ‘মানুষ’ (মূর্ধন্য-ষ)।"),
    SpellingRule("কবীতা", "কবিতা", "কার ও ফলা", "‘কবিতা’ শব্দে হ্রস্ব ই-কার বসে।"),
    SpellingRule("উপন্যস", "উপন্যাস", "কার ও ফলা", "সঠিক বানান ‘উপন্যাস’ (ন্য-এর পর আ-কার)।"),
    SpellingRule("কারনে", "কারণে", "বানান ভুল", "ণত্ব বিধান অনুযায়ী সঠিক বানান ‘কারণে’।"),
    SpellingRule("হঠাত", "হঠাৎ", "বানান ভুল", "প্রমিত বাংলা বানানে খণ্ড-ৎ বসে: ‘হঠাৎ’।"),
    SpellingRule("সাভাবিক", "স্বাভাবিক", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘স্বাভাবিক’ (ব-ফলাসহ)।"),
    SpellingRule("পরিস্থতি", "পরিস্থিতি", "কার ও ফলা", "সঠিক বানান ‘পরিস্থিতি’।"),
    SpellingRule("অনুভুতি", "অনুভূতি", "কার ও ফলা", "‘অনুভূতি’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("সহানুভুতি", "সহানুভূতি", "কার ও ফলা", "‘সহানুভূতি’ শব্দে দীর্ঘ ঊ-কার বসে।"),
    SpellingRule("সম্মুখিন", "সম্মুখীন", "কার ও ফলা", "‘সম্মুখীন’ শব্দে দীর্ঘ ঈ-কার বসে।"),
    SpellingRule("সৌন্দর্যতা", "সৌন্দর্য", "ব্যাকরণগত ভুল", "‘সৌন্দর্য’ নিজেই বিশেষ্য; এর সাথে ‘-তা’ প্রত্যয় বাহুল্য দোষ।"),
    SpellingRule("মাধুর্যতা", "মাধুর্য", "ব্যাকরণগত ভুল", "‘মাধুর্য’ নিজেই বিশেষ্য; এর সাথে ‘-তা’ প্রত্যয় বাহুল্য দোষ।"),
    SpellingRule("উপরোক্ত", "উপর্যুক্ত", "বানান ভুল", "প্রমিত বাংলায় ‘উপরোক্ত’-র পরিবর্তে ‘উপর্যুক্ত’ শুদ্ধ।"),
    SpellingRule("নূন্যতম", "ন্যূনতম", "কার ও ফলা", "সঠিক প্রমিত বানান ‘ন্যূনতম’।"),
    SpellingRule("মুহুর্তে", "মুহূর্তে", "কার ও ফলা", "দ্বিতীয় অক্ষরে দীর্ঘ ঊ-কার বসে: ‘মুহূর্তে’।"),
    SpellingRule("দুরত্বে", "দূরত্বে", "কার ও ফলা", "‘দূর’ শব্দে দীর্ঘ ঊ-কার বসে: ‘দূরত্বে’।"),
    SpellingRule("অসম্বব", "অসম্ভব", "যুক্তাক্ষরের ভুল", "সঠিক বানান ‘অসম্ভব’ (ম্ভ যুক্তাক্ষরসহ)।"),
    SpellingRule("কিন্ত", "কিন্তু", "কার ও ফলা", "সঠিক বানান ‘কিন্তু’ (উ-কারসহ)।")
  ).filterNotNull()

  private val compiledBoundaryRules: List<Pair<SpellingRule, Regex>> by lazy {
    commonBengaliRules.map { rule ->
      rule to Regex("(?<![\\u0980-\\u09FF])${Regex.escape(rule.wrong)}(?![\\u0980-\\u09FF])")
    }
  }

  private val bengaliDotRegex = Regex("([\\u0980-\\u09FF]{2,})\\.(?=\\s|$)")
  private val spaceBeforePunctRegex = Regex("([\\u0980-\\u09FF]+)\\s+([,।?!;:])")
  private val doubleSpaceRegex = Regex("([^\\s\\n])  +([^\\s\\n])")
  private val dupWordRegex = Regex("([\\u0980-\\u09FF]{3,})\\s+\\1(?![\\u0980-\\u09FF])")
  private val missingSpaceAfterDariRegex = Regex("([\\u0980-\\u09FF]+)।([\\u0980-\\u09FF]+)")
  private val doubleKarRegex = Regex("[\\u0980-\\u09FF]*([\\u09BE-\\u09C4\\u09C7\\u09C8\\u09CB\\u09CC\\u09CD])\\1[\\u0980-\\u09FF]*")
  private val doublePunctRegex = Regex("([।,])\\1+")

  private val naturalReduplications = setOf(
    "ধীরে", "আস্তে", "বার", "ঘন", "ছোট", "বড়", "মাঝে", "পাশে", "সাথে",
    "সঙ্গে", "দিন", "রাত", "এক", "কথায়", "হঠাৎ", "বেশি", "নতুন", "কাছে", "দূরে", "নিজে",
    "ফিরে", "চলে", "হেসে", "কেঁদে", "নেমে", "ভেসে"
  )

  private const val MAX_LOCAL_ISSUES = 300

  fun runLocalProofreadingChecks(
    text: String,
    category: ManuscriptCategory
  ): List<ProofreadingIssue> {
    if (text.isBlank()) return emptyList()
    val issues = mutableListOf<ProofreadingIssue>()
    val paragraphs = text.split(Regex("\\n\\s*\\n+"))
    var issueCounter = 1

    for ((pIdx, para) in paragraphs.withIndex()) {
      if (issues.size >= MAX_LOCAL_ISSUES) break

      // 1. Dictionary spelling / kar-fola / juktakkhor checks (fast substring pre-filter + compiled boundary regex)
      for ((rule, boundaryRegex) in compiledBoundaryRules) {
        if (issues.size >= MAX_LOCAL_ISSUES) break
        // In poetry, skip stylistic 'জোছনা' suggestion to strictly preserve poet's diction
        if (category == ManuscriptCategory.POETRY && rule.wrong == "জোছনা") continue
        if (para.contains(rule.wrong) && boundaryRegex.containsMatchIn(para)) {
          issues.add(
            ProofreadingIssue(
              id = "local_${issueCounter++}",
              originalText = rule.wrong,
              suggestedText = rule.right,
              category = rule.category,
              severity = "গুরুত্বপূর্ণ",
              explanation = rule.explanation,
              paragraphIndex = pIdx,
              poetryPreservationNote = if (category == ManuscriptCategory.POETRY) {
                "কবিতার ছন্দ ও মাত্রা অক্ষুণ্ণ রেখে কেবল প্রমিত বানান সংশোধনের পরামর্শ।"
              } else "",
              source = "LOCAL"
            )
          )
        }
      }

      // 2. ASCII full-stop '.' used at the end of a Bengali sentence instead of দাঁড়ি '।'
      if (para.contains('.')) {
        bengaliDotRegex.findAll(para).forEach { match ->
          if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
          val full = match.value
          val word = match.groupValues[1]
          if (word.length >= 2 && word !in setOf("ড", "মো", "প্রো", "ডা", "খ্রি", "নং")) {
            issues.add(
              ProofreadingIssue(
                id = "local_${issueCounter++}",
                originalText = full,
                suggestedText = "$word।",
                category = "বিরামচিহ্নের ভুল",
                severity = "জরুরি",
                explanation = "বাংলা বাক্যের শেষে ইংরেজি ফুলস্টপ (.)-এর পরিবর্তে বাংলা দাঁড়ি (।) ব্যবহার করা শুদ্ধ।",
                paragraphIndex = pIdx,
                poetryPreservationNote = if (category == ManuscriptCategory.POETRY) {
                  "কবিতার পঙ্ক্তির বিরতি ও ছন্দ অপরিবর্তিত থাকবে।"
                } else "",
                source = "LOCAL"
              )
            )
          }
        }
      }

      // 3. Space before comma, dari, question mark, or exclamation
      spaceBeforePunctRegex.findAll(para).take(5).forEach { match ->
        if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
        val original = match.value
        val fixed = "${match.groupValues[1]}${match.groupValues[2]}"
        issues.add(
          ProofreadingIssue(
            id = "local_${issueCounter++}",
            originalText = original,
            suggestedText = fixed,
            category = "টাইপিং ভুল",
            severity = "পরামর্শ",
            explanation = "বিরামচিহ্নের আগে অপ্রয়োজনীয় ফাঁকা স্থান (স্পেস) রয়েছে।",
            paragraphIndex = pIdx,
            poetryPreservationNote = if (category == ManuscriptCategory.POETRY) {
              "কবিতার পঙ্ক্তি বিন্যাস অক্ষুণ্ণ রেখে কেবল অতিরিক্ত স্পেস সরানোর পরামর্শ।"
            } else "",
            source = "LOCAL"
          )
        )
      }

      // 4. Double spaces within a line
      if (para.contains("  ")) {
        doubleSpaceRegex.findAll(para).take(3).forEach { match ->
          if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
          issues.add(
            ProofreadingIssue(
              id = "local_${issueCounter++}",
              originalText = match.value,
              suggestedText = "${match.groupValues[1]} ${match.groupValues[2]}",
              category = "টাইপিং ভুল",
              severity = "পরামর্শ",
              explanation = "দুটি শব্দের মাঝে একাধিক ফাঁকা স্থান পাওয়া গেছে।",
              paragraphIndex = pIdx,
              source = "LOCAL"
            )
          )
        }
      }

      // 5. Immediate duplicate consecutive word (prose only; poetry reduplication strictly preserved)
      if (category == ManuscriptCategory.PROSE) {
        dupWordRegex.findAll(para).take(5).forEach { match ->
          if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
          val word = match.groupValues[1]
          if (word !in naturalReduplications) {
            issues.add(
              ProofreadingIssue(
                id = "local_${issueCounter++}",
                originalText = match.value,
                suggestedText = word,
                category = "পুনরাবৃত্ত শব্দ",
                severity = "পরামর্শ",
                explanation = "একই শব্দ পরপর দুবার টাইপ হয়ে থাকতে পারে; অভিপ্রেত না হলে একবার রাখুন।",
                paragraphIndex = pIdx,
                source = "LOCAL"
              )
            )
          }
        }
      }

      // 6. Accidental duplicate Bengali vowel/kar/hasanta signs (e.g., 'াা', 'িি', '্্')
      doubleKarRegex.findAll(para).take(5).forEach { match ->
        if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
        val brokenWord = match.value
        val fixedWord = brokenWord.replace(Regex("([\\u09BE-\\u09C4\\u09C7\\u09C8\\u09CB\\u09CC\\u09CD])\\1+"), "$1")
        if (fixedWord != brokenWord) {
          issues.add(
            ProofreadingIssue(
              id = "local_${issueCounter++}",
              originalText = brokenWord,
              suggestedText = fixedWord,
              category = "কার ও ফলা",
              severity = "জরুরি",
              explanation = "একই কার-চিহ্ন বা হসন্ত পরপর দুবার টাইপ হয়েছে—একটি সরানো প্রয়োজন।",
              paragraphIndex = pIdx,
              poetryPreservationNote = if (category == ManuscriptCategory.POETRY) {
                "কবিতার শব্দ ও ছন্দ অক্ষুণ্ণ রেখে কেবল টাইপিং ত্রুটি সংশোধনের পরামর্শ।"
              } else "",
              source = "LOCAL"
            )
          )
        }
      }

      // 7. Missing space after Bengali দাঁড়ি (।) directly followed by next word
      if (para.contains('।')) {
        missingSpaceAfterDariRegex.findAll(para).take(5).forEach { match ->
          if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
          issues.add(
            ProofreadingIssue(
              id = "local_${issueCounter++}",
              originalText = match.value,
              suggestedText = "${match.groupValues[1]}। ${match.groupValues[2]}",
              category = "বিরামচিহ্নের ভুল",
              severity = "গুরুত্বপূর্ণ",
              explanation = "দাঁড়ির (।) পর পরবর্তী বাক্য শুরুর আগে একটি ফাঁকা স্থান (স্পেস) থাকা প্রয়োজন।",
              paragraphIndex = pIdx,
              poetryPreservationNote = if (category == ManuscriptCategory.POETRY) {
                "পঙ্ক্তির বিন্যাস অক্ষুণ্ণ রেখে কেবল দাঁড়ির পর স্পেস যুক্ত করার পরামর্শ।"
              } else "",
              source = "LOCAL"
            )
          )
        }
      }

      // 8. Accidental double dari or double comma in prose
      if (category == ManuscriptCategory.PROSE) {
        doublePunctRegex.findAll(para).take(3).forEach { match ->
          if (issues.size >= MAX_LOCAL_ISSUES) return@forEach
          issues.add(
            ProofreadingIssue(
              id = "local_${issueCounter++}",
              originalText = match.value,
              suggestedText = match.groupValues[1],
              category = "বিরামচিহ্নের ভুল",
              severity = "পরামর্শ",
              explanation = "একই বিরামচিহ্ন পরপর একাধিকবার টাইপ হয়েছে।",
              paragraphIndex = pIdx,
              source = "LOCAL"
            )
          )
        }
      }
    }

    return issues.distinctBy { "${it.paragraphIndex}|${it.originalText}|${it.suggestedText}" }
  }

  /**
   * Boundary-safe replacement helper: avoids corrupting longer Bengali words
   * (e.g. matching 'প্রান' inside another word) or ellipses ('হলো...').
   */
  fun replaceFirstSafely(source: String, original: String, replacement: String): String {
    if (original.isEmpty() || !source.contains(original)) return source
    val pureBengali = original.all { it.code in 0x0980..0x09FF }
    if (pureBengali) {
      val regex = Regex("(?<![\\u0980-\\u09FF])${Regex.escape(original)}(?![\\u0980-\\u09FF])")
      val match = regex.find(source)
      if (match != null) {
        return source.replaceRange(match.range, replacement)
      }
    } else if (original.endsWith(".") && !original.endsWith("..")) {
      val regex = Regex("${Regex.escape(original)}(?!\\.)")
      val match = regex.find(source)
      if (match != null) {
        return source.replaceRange(match.range, replacement)
      }
    }
    return source.replaceFirst(original, replacement)
  }

  /**
   * Detects headings, chapters, sub-chapters, and poem titles to build a Table of Contents.
   */
  fun extractTableOfContents(text: String): List<TocEntry> {
    if (text.isBlank()) return emptyList()
    val entries = mutableListOf<TocEntry>()
    val paragraphs = text.split(Regex("\\n\\s*\\n+"))

    val chapterPrefixRegex = Regex(
      "^(?:#{1,3}\\s*|অধ্যায়\\s*[০-৯0-9]+|পরিচ্ছেদ\\s*[০-৯0-9]+|পর্ব\\s*[০-৯0-9]+|খণ্ড\\s*[০-৯0-9]+|ভূমিকা|উৎসর্গ|শেষাংশ|উপসংহার|কবিতা\\s*[০-৯0-9]+|বিভাগ\\s*[০-৯0-9]+|[০-৯0-9]+[.)]\\s+).*"
    )

    paragraphs.forEachIndexed { idx, rawPara ->
      val firstLine = rawPara.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return@forEachIndexed
      val wordsInPara = rawPara.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

      val isMarkdownHeading = firstLine.startsWith("#")
      val isExplicitChapter = chapterPrefixRegex.matches(firstLine)
      val isShortStandaloneTitle = rawPara.lines().count { it.isNotBlank() } == 1 &&
        firstLine.length in 3..60 &&
        !firstLine.endsWith("।") &&
        !firstLine.endsWith(",") &&
        !firstLine.endsWith("?") &&
        !firstLine.endsWith("!") &&
        paragraphs.size > 2

      if (isMarkdownHeading || isExplicitChapter || isShortStandaloneTitle) {
        val level = when {
          firstLine.startsWith("##") -> 2
          firstLine.startsWith("#") -> 1
          firstLine.startsWith("উপ-অধ্যায়") -> 2
          isExplicitChapter -> 1
          else -> 2
        }
        val cleanTitle = firstLine.replace(Regex("^#+\\s*"), "").trim()
        entries.add(
          TocEntry(
            title = cleanTitle,
            level = level,
            paragraphIndex = idx,
            wordCount = wordsInPara
          )
        )
      }
    }
    return entries
  }

  /**
   * Deterministically detects duplicate paragraphs and repeated sentences.
   */
  fun findRepeatedSegments(text: String): List<RepeatedSegment> {
    if (text.isBlank()) return emptyList()
    val results = mutableListOf<RepeatedSegment>()

    // 1. Duplicate paragraphs
    val paragraphs = text.split(Regex("\\n\\s*\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
    val paraMap = mutableMapOf<String, MutableList<Int>>()
    paragraphs.forEachIndexed { index, para ->
      val normalized = para.replace(Regex("\\s+"), " ").trim()
      if (normalized.length >= 20) {
        paraMap.getOrPut(normalized) { mutableListOf() }.add(index + 1)
      }
    }
    paraMap.forEach { (para, locs) ->
      if (locs.size > 1) {
        results.add(
          RepeatedSegment(
            text = if (para.length > 140) para.take(140) + "…" else para,
            occurrences = locs.size,
            locations = locs,
            type = "পুনরাবৃত্ত অনুচ্ছেদ"
          )
        )
      }
    }

    // 2. Repeated sentences
    val sentences = text.split(Regex("[।?!\\n]+"))
      .map { it.replace(Regex("\\s+"), " ").trim() }
      .filter { it.length >= 18 }
    val sentenceMap = mutableMapOf<String, MutableList<Int>>()
    sentences.forEachIndexed { index, sentence ->
      sentenceMap.getOrPut(sentence) { mutableListOf() }.add(index + 1)
    }
    sentenceMap.forEach { (sentence, locs) ->
      if (locs.size > 1 && results.none { it.text.contains(sentence.take(30)) }) {
        results.add(
          RepeatedSegment(
            text = if (sentence.length > 120) sentence.take(120) + "…" else sentence,
            occurrences = locs.size,
            locations = locs,
            type = "পুনরাবৃত্ত বাক্য"
          )
        )
      }
    }

    return results
  }

  /**
   * Local formatting consistency checks for Manuscript Organizer.
   */
  fun detectFormattingIssues(text: String, category: ManuscriptCategory): Pair<List<String>, List<String>> {
    val formattingIssues = mutableListOf<String>()
    val missingHeadings = mutableListOf<String>()

    if (text.contains(Regex("\\n{4,}"))) {
      formattingIssues.add("অনুচ্ছেদের মাঝে তিনটির বেশি অতিরিক্ত ফাঁকা লাইন রয়েছে।")
    }
    if (text.contains(Regex("  +"))) {
      formattingIssues.add("লেখার ভেতরে একাধিক অতিরিক্ত স্পেস (ফাঁকা জায়গা) পাওয়া গেছে।")
    }
    if (text.contains(Regex("[\\u0980-\\u09FF]{2,}\\.(?=\\s|$)"))) {
      formattingIssues.add("কিছু বাংলা বাক্যে দাঁড়ির (।) পরিবর্তে ইংরেজি ফুলস্টপ (.) ব্যবহৃত হয়েছে।")
    }
    if (text.contains("\t")) {
      formattingIssues.add("অসঙ্গতিপূর্ণ ট্যাব ইনডেন্টেশন পাওয়া গেছে।")
    }

    val toc = extractTableOfContents(text)
    val metrics = BengaliUtils.calculateMetrics(text)
    if (metrics.paragraphs >= 4 && toc.isEmpty()) {
      if (category == ManuscriptCategory.POETRY) {
        missingHeadings.add("কবিতা সংকলনে কোনো শিরোনাম বা বিভাগ শনাক্ত হয়নি—কবিতার শিরোনাম ও বিভাগ যুক্ত করার পরামর্শ।")
      } else {
        missingHeadings.add("দীর্ঘ গদ্যে কোনো অধ্যায় বা উপ-অধ্যায়ের শিরোনাম পাওয়া যায়নি।")
      }
    }

    return formattingIssues to missingHeadings
  }

  /**
   * Builds a clean, whitespace-normalized version of the manuscript WITHOUT rearranging poems or altering wording.
   */
  fun buildCleanFormattedDraft(text: String, category: ManuscriptCategory): String {
    val normalizedLineEndings = text.replace("\r\n", "\n").replace("\r", "\n")
    val cleanedLines = normalizedLineEndings.lines().map { line ->
      val trimmedEnd = line.trimEnd()
      if (category == ManuscriptCategory.POETRY) {
        // Preserve leading poetic indentation while removing trailing noise
        trimmedEnd.replace(Regex("(?<=\\S) {2,}"), " ")
      } else {
        trimmedEnd.trim().replace(Regex(" {2,}"), " ")
      }
    }
    return cleanedLines.joinToString("\n")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  // =========================================================================
  // ON-DEVICE BENGALI COMPUTATIONAL STYLOMETRY (AI WRITING DETECTOR FALLBACK)
  // =========================================================================

  private val mechanicalBengaliMarkers = listOf(
    "সর্বোপরি",
    "পরিশেষে বলা যায়",
    "পরিশেষে",
    "উপসংহারে বলা যায়",
    "উপসংহারে",
    "অন্যদিকে এটি লক্ষণীয়",
    "এটি অত্যন্ত গুরুত্বপূর্ণ যে",
    "গুরুত্বপূর্ণ ভূমিকা পালন করে",
    "তাৎপর্যপূর্ণভাবে",
    "বিভিন্ন দিক থেকে",
    "সারসংক্ষেপে",
    "নিঃসন্দেহে বলা যায়",
    "অতএব বলা যায়",
    "উল্লেখযোগ্য বিষয় হলো",
    "ফলস্বরূপ",
    "সামগ্রিকভাবে বলা যায়",
    "এটি স্পষ্ট যে",
    "উল্লেখ্য যে"
  )

  private val humanConversationalMarkers = listOf(
    "কিন্তু", "তবে", "আসলে", "হঠাৎ", "যেন", "তবুও", "তখন", "হয়তো", "বোধহয়", "আহা", "ওহ", "কেননা", "মনে হলো"
  )

  /**
   * Scientifically honest, deterministic on-device Bengali stylometric analyzer.
   * Computes real sentence-length burstiness (CV), lexical diversity (TTR), mechanical
   * Bengali transition cliché density, and paragraph structural uniformity directly from the text.
   */
  fun runLocalAiDetection(
    text: String,
    category: ManuscriptCategory,
    disclaimer: String,
    durationMs: Long
  ): AiDetectionReport {
    val metrics = BengaliUtils.calculateMetrics(text)
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val sentences = text.split(Regex("[।?!\\n]+"))
      .map { it.trim() }
      .filter { it.length >= 6 }
    val paragraphs = text.split(Regex("\\n\\s*\\n+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    // Scientific honesty rule: short texts (< 40 words) cannot be classified reliably
    if (metrics.words < 40 || sentences.size < 2) {
      return AiDetectionReport(
        classification = AiDetectionLabel.UNCERTAIN.name,
        detectionIndicatorLevel = "অপর্যাপ্ত তথ্য (পাঠ্যটি সংক্ষিপ্ত)",
        aiLikelihoodRange = "নির্ণয়যোগ্য নয় — অন্তত ৪০ শব্দের বেশি পাঠ্য প্রয়োজন",
        humanLikelihoodRange = "নির্ণয়যোগ্য নয় — পর্যাপ্ত বাক্যবৈচিত্র্য মূল্যায়নের জন্য আরও পাঠ্য প্রয়োজন",
        confidenceLevel = ConfidenceLevel.LOW.name,
        verifiedAuthorshipNote = "অযাচাইকৃত — কোনো স্বয়ংক্রিয় পদ্ধতি প্রকৃত লেখকত্ব প্রমাণ করতে পারে না।",
        analysisMode = "স্থানীয় বাংলা ভাষাতাত্ত্বিক বিশ্লেষণ",
        explanation = "প্রদত্ত পাঠ্যে মাত্র ${BengaliUtils.toBengaliDigits(metrics.words)}টি শব্দ রয়েছে। ৪০ শব্দের কম পাঠ্যে বাক্যের ছন্দ, পুনরাবৃত্তি বা শৈলীগত প্রবণতা নির্ভরযোগ্যভাবে নির্ণয় করা যায় না, তাই ফলাফল ‘অনিশ্চিত’ হিসেবে দেখানো হয়েছে।",
        overallCharacteristics = listOf(
          "মোট শব্দ সংখ্যা: ${BengaliUtils.toBengaliDigits(metrics.words)}",
          "মোট বাক্য/পঙ্ক্তি: ${BengaliUtils.toBengaliDigits(sentences.size)}",
          "আরও দীর্ঘ পাঠ্য যুক্ত করলে ১০টি ভাষাতাত্ত্বিক মাত্রা বিস্তারিত বিশ্লেষণ করা যাবে।"
        ),
        linguisticDimensions = listOf(
          LinguisticDimensionScore("বাক্যের দৈর্ঘ্য ও ছন্দের সমরূপতা", "অপর্যাপ্ত বাক্যসংখ্যা", "মিশ্র"),
          LinguisticDimensionScore("মানবিক লেখার স্বাভাবিক বৈচিত্র্য", "সংক্ষিপ্ত পাঠ্যে নির্ণয়যোগ্য নয়", "মিশ্র")
        ),
        suspiciousSentences = emptyList(),
        paragraphAnalysis = paragraphs.take(3).mapIndexed { idx, para ->
          ParagraphAiNote(
            paragraphIndex = idx,
            excerpt = para.take(70) + if (para.length > 70) "…" else "",
            assessment = "অপর্যাপ্ত তথ্য",
            observation = "নির্ভরযোগ্য স্টাইলোমেট্রিক মূল্যায়নের জন্য অনুচ্ছেদটি সংক্ষিপ্ত।"
          )
        },
        uncertaintyWarning = disclaimer,
        durationMs = durationMs,
        fromCache = false
      )
    }

    // 1. Sentence length mean & coefficient of variation (burstiness)
    val sentLengths = sentences.map { s -> s.split(Regex("\\s+")).count { it.isNotBlank() }.toDouble() }
    val meanSentLen = sentLengths.average().coerceAtLeast(1.0)
    val variance = sentLengths.map { (it - meanSentLen) * (it - meanSentLen) }.average()
    val cv = sqrt(variance) / meanSentLen // Lower CV (<0.28) = robotic uniformity; higher CV (>0.45) = human burstiness

    // 2. Lexical diversity (Type-Token Ratio on normalized tokens)
    val normalizedTokens = words.map { it.replace(Regex("[।,?!;:\"'()\\[\\]]"), "").trim() }.filter { it.isNotEmpty() }
    val ttr = if (normalizedTokens.isNotEmpty()) {
      normalizedTokens.toSet().size.toDouble() / normalizedTokens.size.toDouble()
    } else 0.5

    // 3. Mechanical Bengali AI cliché markers
    val matchedMarkers = mutableListOf<String>()
    val suspiciousSentences = mutableListOf<SuspiciousSentence>()
    for (sentence in sentences) {
      val hitMarkers = mechanicalBengaliMarkers.filter { sentence.contains(it) }
      if (hitMarkers.isNotEmpty()) {
        matchedMarkers.addAll(hitMarkers)
        if (suspiciousSentences.size < 8) {
          suspiciousSentences.add(
            SuspiciousSentence(
              sentence = sentence.take(180),
              reason = "অনুমেয় বা অনুবাদসুলভ বাংলা সংযোগশব্দ পাওয়া গেছে: ‘${hitMarkers.joinToString("’, ‘")}’।",
              signalStrength = if (hitMarkers.size >= 2) "বেশি" else "মাঝারি"
            )
          )
        }
      }
    }

    // 4. Repeated sentence openers
    val openers = sentences.mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.take(12) }
    val openerDupRatio = if (openers.size >= 4) {
      1.0 - (openers.toSet().size.toDouble() / openers.size.toDouble())
    } else 0.0

    // 5. Human natural / conversational markers
    val humanHits = humanConversationalMarkers.count { text.contains(it) }

    // Composite heuristic score (0..100 mechanical signal index, calibrated conservatively for Bengali literature)
    var aiSignal = 28
    if (matchedMarkers.isNotEmpty()) aiSignal += (matchedMarkers.size * 14).coerceAtMost(38)
    if (cv < 0.25 && category == ManuscriptCategory.PROSE) aiSignal += 18
    else if (cv < 0.34 && category == ManuscriptCategory.PROSE) aiSignal += 8
    else if (cv >= 0.45) aiSignal -= 14

    if (openerDupRatio > 0.35 && category == ManuscriptCategory.PROSE) aiSignal += 12
    if (ttr < 0.42 && normalizedTokens.size < 300) aiSignal += 8
    else if (ttr > 0.62) aiSignal -= 10

    if (humanHits >= 3) aiSignal -= 10
    if (category == ManuscriptCategory.POETRY) aiSignal -= 12 // Poetry naturally has uniform rhythm & repetition
    aiSignal = aiSignal.coerceIn(8, 86)

    val (classification, indicator, aiRange, humanRange, confidence) = when {
      aiSignal >= 62 -> listOf(
        AiDetectionLabel.LIKELY_AI.name,
        "মাঝারি-উচ্চ যান্ত্রিক প্রবণতা",
        "মাঝারি থেকে বেশি (একাধিক গতানুগতিক বাংলা সংযোগশব্দ ও সমরূপ বাক্যগঠন)",
        "তুলনামূলক কম (স্বাভাবিক বাক্যবৈচিত্র্য সীমিত)",
        ConfidenceLevel.MEDIUM.name
      )
      aiSignal >= 42 -> listOf(
        AiDetectionLabel.MIXED.name,
        "মাঝারি মিশ্র প্রবণতা",
        "মাঝারি (কিছু অনুমেয় বাক্যকাঠামো ও আনুষ্ঠানিক প্রবাহ দৃশ্যমান)",
        "মাঝারি (মানবিক অভিব্যক্তি ও শৈলীর উপস্থিতি রয়েছে)",
        ConfidenceLevel.MEDIUM.name
      )
      else -> listOf(
        AiDetectionLabel.LIKELY_HUMAN.name,
        "নিম্ন যান্ত্রিক প্রবণতা (স্বাভাবিক মানবিক বৈশিষ্ট্য)",
        "কম (যান্ত্রিক বা অনুবাদসুলভ লক্ষণ সামান্য)",
        "বেশি (স্বাভাবিক বাংলা বাক্যছন্দ, শব্দবৈচিত্র্য ও প্রকাশভঙ্গি স্পষ্ট)",
        if (metrics.words >= 120) ConfidenceLevel.MEDIUM.name else ConfidenceLevel.LOW.name
      )
    }

    val cvPct = BengaliUtils.toBengaliDigits((cv * 100).toInt())
    val ttrPct = BengaliUtils.toBengaliDigits((ttr * 100).toInt())
    val markerCountBn = BengaliUtils.toBengaliDigits(matchedMarkers.size)

    val dimensions = listOf(
      LinguisticDimensionScore(
        "১. বাক্য গঠনের পুনরাবৃত্তি",
        if (openerDupRatio > 0.35) "একই ধরনের বাক্য-শুরুর পুনরাবৃত্তি লক্ষ্য করা গেছে।" else "বাক্য গঠনে স্বাভাবিক বৈচিত্র্য রয়েছে।",
        if (openerDupRatio > 0.35) "যান্ত্রিক" else "মানবিক"
      ),
      LinguisticDimensionScore(
        "২. বাক্যের দৈর্ঘ্য ও ছন্দের সমরূপতা",
        "বাক্যের দৈর্ঘ্যের বৈচিত্র্য সূচক $cvPct% (গড় ${BengaliUtils.toBengaliDigits(meanSentLen.toInt())} শব্দ/বাক্য)।",
        when {
          cv < 0.25 && category == ManuscriptCategory.PROSE -> "যান্ত্রিক"
          cv < 0.38 -> "মিশ্র"
          else -> "মানবিক"
        }
      ),
      LinguisticDimensionScore(
        "৩. শব্দভান্ডারের পুনরাবৃত্তি",
        "অনন্য শব্দের অনুপাত (Lexical Diversity) প্রায় $ttrPct%।",
        if (ttr >= 0.52) "মানবিক" else "মিশ্র"
      ),
      LinguisticDimensionScore(
        "৪. গতানুগতিক ও অনুবাদসুলভ অভিব্যক্তি",
        if (matchedMarkers.isEmpty()) "অনুবাদসুলভ বা যান্ত্রিক অভিব্যক্তি শনাক্ত হয়নি।" else "মোট ${markerCountBn}টি গতানুগতিক অভিব্যক্তি পাওয়া গেছে।",
        if (matchedMarkers.size >= 2) "যান্ত্রিক" else if (matchedMarkers.size == 1) "মিশ্র" else "মানবিক"
      ),
      LinguisticDimensionScore(
        "৫. অনুমেয় সংযোগশব্দ ও রূপান্তর",
        if (matchedMarkers.isNotEmpty()) "‘${matchedMarkers.distinct().take(3).joinToString("’, ‘")}’ জাতীয় সংযোগশব্দ ব্যবহৃত হয়েছে।" else "সংযোগশব্দের ব্যবহার স্বাভাবিক ও প্রাসঙ্গিক।",
        if (matchedMarkers.size >= 2) "যান্ত্রিক" else "মানবিক"
      ),
      LinguisticDimensionScore(
        "৬. অনুচ্ছেদের কাঠামোগত পুনরাবৃত্তি",
        "মোট ${BengaliUtils.toBengaliDigits(paragraphs.size)}টি অনুচ্ছেদের কাঠামো পরীক্ষা করা হয়েছে।",
        if (paragraphs.size >= 3 && paragraphs.map { it.length }.distinct().size == 1) "যান্ত্রিক" else "মানবিক"
      ),
      LinguisticDimensionScore(
        "৭. শৈলীগত ধারাবাহিকতা",
        if (category == ManuscriptCategory.POETRY) "কবিতার ছন্দ ও পঙ্ক্তি বিন্যাস সংরক্ষিত রেখে মূল্যায়ন করা হয়েছে।" else "গদ্যের বর্ণনাভঙ্গি ও প্রবাহ স্থিতিশীল রয়েছে।",
        "মানবিক"
      ),
      LinguisticDimensionScore(
        "৮. অস্বাভাবিক ভাষাতাত্ত্বিক প্যাটার্ন",
        if (suspiciousSentences.isEmpty()) "উল্লেখযোগ্য অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি।" else "${BengaliUtils.toBengaliDigits(suspiciousSentences.size)}টি বাক্যে পুনরাবৃত্ত বা আনুষ্ঠানিক প্রবণতা রয়েছে।",
        if (suspiciousSentences.size >= 2) "মিশ্র" else "মানবিক"
      ),
      LinguisticDimensionScore(
        "৯. প্রাসঙ্গিক সামঞ্জস্য ও গভীরতা",
        "শব্দসংখ্যা (${BengaliUtils.toBengaliDigits(metrics.words)}) ও বাক্য-পরিসরের ভিত্তিতে বিষয়বস্তুর ধারাবাহিকতা বজায় আছে।",
        "মানবিক"
      ),
      LinguisticDimensionScore(
        "১০. মানবিক লেখার স্বাভাবিক বৈচিত্র্য",
        if (humanHits >= 2 || cv >= 0.4) "স্বাভাবিক বাংলা প্রকাশভঙ্গি ও বাক্যছন্দের ওঠানামা স্পষ্ট।" else "বাক্যের প্রবাহ তুলনামূলক সমতল ও আনুষ্ঠানিক।",
        if (humanHits >= 2 || cv >= 0.4) "মানবিক" else "মিশ্র"
      )
    )

    val paraNotes = paragraphs.take(10).mapIndexed { idx, para ->
      val pHits = mechanicalBengaliMarkers.filter { para.contains(it) }
      val assessment = when {
        pHits.size >= 2 -> "যান্ত্রিক বৈশিষ্ট্য"
        pHits.size == 1 -> "মিশ্র বৈশিষ্ট্য"
        else -> "মানবিক বৈশিষ্ট্য"
      }
      val obs = if (pHits.isNotEmpty()) {
        "এই অনুচ্ছেদে গতানুগতিক সংযোগশব্দ (‘${pHits.joinToString("’, ‘")}’) ব্যবহৃত হয়েছে।"
      } else {
        "অনুচ্ছেদের বাক্যগঠন ও শব্দচয়ন স্বাভাবিক বাংলা প্রবাহ অনুসরণ করেছে।"
      }
      ParagraphAiNote(
        paragraphIndex = idx,
        excerpt = para.replace('\n', ' ').take(72) + if (para.length > 72) "…" else "",
        assessment = assessment,
        observation = obs
      )
    }

    val characteristics = buildList {
      add("মোট শব্দ: ${BengaliUtils.toBengaliDigits(metrics.words)} · বাক্য: ${BengaliUtils.toBengaliDigits(sentences.size)} · গড় বাক্য দৈর্ঘ্য: ${BengaliUtils.toBengaliDigits(meanSentLen.toInt())} শব্দ")
      add("বাক্য-ছন্দের বৈচিত্র্য সূচক: $cvPct% · শব্দবৈচিত্র্য: $ttrPct%")
      if (matchedMarkers.isNotEmpty()) {
        add("শনাক্তকৃত গতানুগতিক সংযোগশব্দ: ${matchedMarkers.distinct().take(4).joinToString(", ")}")
      } else {
        add("কোনো গতানুগতিক অনুবাদসুলভ AI সংযোগশব্দ শনাক্ত হয়নি")
      }
    }

    val explanation = buildString {
      append("স্থানীয় বাংলা স্টাইলোমেট্রি ইঞ্জিনে পাঠ্যের ১০টি ভাষাতাত্ত্বিক মাত্রা পরীক্ষা করা হয়েছে। ")
      if (matchedMarkers.isNotEmpty()) {
        append("পাঠ্যে ${markerCountBn}টি অনুমেয়/গতানুগতিক বাংলা সংযোগশব্দ শনাক্ত হয়েছে এবং ")
      } else {
        append("পাঠ্যে কোনো প্রচলিত যান্ত্রিক সংযোগশব্দ পাওয়া যায়নি এবং ")
      }
      append("বাক্যদৈর্ঘ্যের বৈচিত্র্য $cvPct% ও শব্দবৈচিত্র্য $ttrPct% রেকর্ড হয়েছে। ")
      append("আরও গভীর AI ক্লাউড বিশ্লেষণের জন্য সেটিংস থেকে আপনার পাবলিক HTTPS AI সার্ভার URL যুক্ত করতে পারেন।")
    }

    return AiDetectionReport(
      classification = classification,
      detectionIndicatorLevel = indicator,
      aiLikelihoodRange = aiRange,
      humanLikelihoodRange = humanRange,
      confidenceLevel = confidence,
      verifiedAuthorshipNote = "অযাচাইকৃত — কোনো স্বয়ংক্রিয় পদ্ধতি প্রকৃত লেখকত্ব প্রমাণ করতে পারে না।",
      analysisMode = "স্থানীয় বাংলা ভাষাতাত্ত্বিক বিশ্লেষণ (সার্ভার ছাড়া)",
      explanation = explanation,
      overallCharacteristics = characteristics,
      linguisticDimensions = dimensions,
      suspiciousSentences = suspiciousSentences,
      paragraphAnalysis = paraNotes,
      uncertaintyWarning = disclaimer,
      durationMs = durationMs,
      fromCache = false
    )
  }

  // =========================================================================
  // ON-DEVICE POETRY-SAFE HUMAN WRITING REFINEMENT FALLBACK
  // =========================================================================

  private data class LocalPhraseRefinement(
    val original: String,
    val refined: String,
    val reason: String,
    val changeType: String
  )

  private val proseHumanRefinements = listOf(
    LocalPhraseRefinement(
      "সর্বোপরি বলা যায় যে,",
      "সব মিলিয়ে,",
      "যান্ত্রিক ও অনুবাদসুলভ শুরু বদলে স্বাভাবিক বাংলা প্রবাহ তৈরি করা হয়েছে।",
      "ছন্দ ও প্রবাহ"
    ),
    LocalPhraseRefinement(
      "সর্বোপরি বলা যায়,",
      "সব মিলিয়ে,",
      "গতানুগতিক সংযোগশব্দ কমিয়ে বাক্যটি আরও সাবলীল করা হয়েছে।",
      "ছন্দ ও প্রবাহ"
    ),
    LocalPhraseRefinement(
      "পরিশেষে বলা যায় যে,",
      "শেষে বলা যায়,",
      "অতিরিক্ত আনুষ্ঠানিক কাঠামো সহজ ও প্রাঞ্জল বাংলায় রূপান্তরিত।",
      "বাক্য গঠন"
    ),
    LocalPhraseRefinement(
      "পরিশেষে বলা যায়,",
      "শেষে বলা যায়,",
      "বাক্যের প্রবাহকে স্বাভাবিক মানবিক ভঙ্গিতে আনা হয়েছে।",
      "বাক্য গঠন"
    ),
    LocalPhraseRefinement(
      "উপসংহারে বলা যায় যে,",
      "সবশেষে,",
      "অনুবাদসুলভ উপসংহার বাদ দিয়ে স্বচ্ছ বাংলা রূপ দেওয়া হয়েছে।",
      "সংযোগ ও ধারাবাহিকতা"
    ),
    LocalPhraseRefinement(
      "অন্যদিকে এটি লক্ষণীয় যে,",
      "অন্যদিকে দেখা যায়,",
      "অতিরিক্ত ভারী ও যান্ত্রিক বাক্যাংশ সরল করা হয়েছে।",
      "শব্দচয়ন"
    ),
    LocalPhraseRefinement(
      "এটি অত্যন্ত গুরুত্বপূর্ণ যে,",
      "গুরুত্বপূর্ণ বিষয় হলো,",
      "ইংরেজি অনুবাদসুলভ গঠন থেকে স্বাভাবিক বাংলা বাক্যরীতিতে রূপান্তর।",
      "বাক্য গঠন"
    ),
    LocalPhraseRefinement(
      "গুরুত্বপূর্ণ ভূমিকা পালন করে",
      "বড় ভূমিকা রাখে",
      "ক্লিশে বাগভঙ্গি বদলে জীবন্ত বাংলা ক্রিয়াপদ ব্যবহৃত হয়েছে।",
      "শব্দচয়ন"
    ),
    LocalPhraseRefinement(
      "তাৎপর্যপূর্ণভাবে",
      "উল্লেখযোগ্যভাবে",
      "অতিরিক্ত যান্ত্রিক ক্রিয়াবিশেষণ সরল ও স্বাভাবিক করা হয়েছে।",
      "শব্দচয়ন"
    ),
    LocalPhraseRefinement(
      "নিঃসন্দেহে বলা যায় যে,",
      "নিঃসন্দেহে,",
      "অপ্রয়োজনীয় শব্দবাহুল্য কমিয়ে বাক্যটিকে দৃঢ় করা হয়েছে।",
      "পুনরাবৃত্তি হ্রাস"
    ),
    LocalPhraseRefinement(
      "অতএব বলা যায় যে,",
      "তাই বলা যায়,",
      "স্বাভাবিক বাংলা সংযোগশব্দে বাক্যপ্রবাহ উন্নত করা হয়েছে।",
      "সংযোগ ও ধারাবাহিকতা"
    ),
    LocalPhraseRefinement(
      "সামগ্রিকভাবে বলা যায়,",
      "মোটের ওপর,",
      "পাঠকের জন্য আরও ঘনিষ্ঠ ও স্বাভাবিক বাংলা ভাষা ব্যবহার করা হয়েছে।",
      "ছন্দ ও প্রবাহ"
    )
  )

  /**
   * Poetry-safe, deterministic local Bengali language refinement pass.
   * Refines stiff/translated prose phrasing + standard Bengali spelling & punctuation
   * while strictly protecting poetic imagery, line breaks, and stanza structure.
   */
  fun runLocalRefinement(
    text: String,
    category: ManuscriptCategory,
    mode: RefinementMode,
    poetryProtectionStrict: Boolean,
    editorialDisclaimer: String,
    durationMs: Long
  ): RefinementReport {
    var refined = text
    val changes = mutableListOf<RefinementChange>()
    var counter = 1

    // 1. In prose (or non-strict poetry), smooth stiff/mechanical Bengali phrases
    if (category == ManuscriptCategory.PROSE || !poetryProtectionStrict) {
      for (rule in proseHumanRefinements) {
        if (refined.contains(rule.original)) {
          refined = refined.replace(rule.original, rule.refined)
          changes.add(
            RefinementChange(
              id = "local_ref_${counter++}",
              originalSegment = rule.original,
              refinedSegment = rule.refined,
              changeReason = rule.reason,
              changeType = rule.changeType
            )
          )
        }
      }
    }

    // 2. Apply local Bangla Academy spelling, punctuation & spacing refinements (poetry-protected)
    val localIssues = runLocalProofreadingChecks(refined, category).take(40)
    for (issue in localIssues) {
      if (issue.originalText.isNotBlank() && refined.contains(issue.originalText)) {
        refined = refined.replaceFirst(issue.originalText, issue.suggestedText)
        changes.add(
          RefinementChange(
            id = "local_ref_${counter++}",
            originalSegment = issue.originalText,
            refinedSegment = issue.suggestedText,
            changeReason = issue.explanation,
            changeType = if (category == ManuscriptCategory.POETRY) "কাব্যিক পরামর্শ" else issue.category
          )
        )
      }
    }

    val summary = if (changes.isEmpty()) {
      "স্থানীয় বাংলা পরিমার্জন ইঞ্জিনে ‘${mode.bengaliLabel}’ অনুযায়ী পরীক্ষা সম্পন্ন—আপনার লেখাটি ইতোমধ্যেই স্বাভাবিক ও পরিচ্ছন্ন রয়েছে। গভীর AI পুনর্লিখনের জন্য সেটিংস থেকে পাবলিক HTTPS AI সার্ভার URL যুক্ত করতে পারেন।"
    } else {
      "স্থানীয় বাংলা পরিমার্জন ইঞ্জিনে ‘${mode.bengaliLabel}’ অনুযায়ী মোট ${BengaliUtils.toBengaliDigits(changes.size)}টি স্বাভাবিক ভাষা ও শুদ্ধতা পরিমার্জন প্রস্তাব করা হয়েছে।"
    }

    val poetryNote = if (category == ManuscriptCategory.POETRY) {
      "কবিতা সুরক্ষা সক্রিয়: কবিতার ছন্দ, স্তবক, পঙ্ক্তির বিন্যাস, উপমা ও কবির নিজস্ব শব্দচয়ন সম্পূর্ণ অক্ষুণ্ণ রাখা হয়েছে।"
    } else ""

    return RefinementReport(
      mode = mode.name,
      category = category.name,
      summary = summary,
      poetryPreservationNote = poetryNote,
      refinedFullText = refined,
      changes = changes,
      editorialDisclaimer = editorialDisclaimer,
      durationMs = durationMs,
      fromCache = false
    )
  }
}
