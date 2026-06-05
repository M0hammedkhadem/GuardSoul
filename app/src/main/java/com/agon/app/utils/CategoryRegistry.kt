package com.agon.app.utils

/**
 * Static category → package map. We hardcode the most popular apps
 * per category so the AI can auto-suggest a default block-list when
 * the user installs GuardSoul for the first time. Inspired by
 * Qustodio's 40-category taxonomy and Net Nanny's "auto-detect"
 * feature.
 *
 * Adding a new app:
 *  1. Drop the package into the matching category (lowercase).
 *  2. Don't duplicate — the first match wins.
 *  3. Watch for package name variants (e.g. `com.facebook.katana`
 *     is the Messenger Lite bundle ID, `com.facebook.orca` is the
 *     main Messenger).
 *
 * Detection is package-name only — we never read app labels because
 * the package is the only thing `AppBlockerService` has cheap
 * access to.
 */
object CategoryRegistry {

    private val map: Map<ContentCategory, Set<String>> = mapOf(
        ContentCategory.SOCIAL_MEDIA to setOf(
            "com.facebook.katana", "com.facebook.lite",
            "com.instagram.android", "com.instagram.lite",
            "com.twitter.android", "com.twitter.android.lite",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.pinterest",
            "com.tumblr",
            "com.burbn.instagram",
            "com.threads.android",
            "mastodon.social",
            "com.bluesky.client"
        ),
        ContentCategory.MESSAGING to setOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger", "org.telegram.plus",
            "com.facebook.orca",
            "org.thoughtcrime.securesms",
            "com.tencent.mm",
            "com.viber.voip",
            "jp.naver.line.android",
            "com.discord",
            "com.Slack",
            "com.microsoft.teams"
        ),
        ContentCategory.ENTERTAINMENT to setOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.tv.twitch.android.app",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.hulu.plus",
            "com.amazon.avod.thirdpartyclient",
            "com.apple.atve.androidtv.appletv",
            "com.peacocktv.peacockandroid",
            "com.cbs.app"
        ),
        ContentCategory.MUSIC to setOf(
            "com.spotify.music",
            "com.apple.android.music",
            "com.soundcloud.android",
            "deezer.android.app",
            "com.aspiro.tidal",
            "com.pandora.android",
            "com.shazam.android"
        ),
        ContentCategory.GAMES to setOf(
            "com.tencent.ig",            // PUBG Mobile
            "com.epicgames.fortnite",
            "com.mojang.minecraftpe",
            "com.roblox.client",
            "com.dts.freefireth",
            "com.supercell.brawlstars",
            "com.supercell.clashofclans",
            "com.supercell.clashroyale",
            "com.miHoYo.GenshinImpact",
            "com.levelinfinite.sgameGlobal",
            "com.mobile.legends",
            "com.garena.game.kgtw",
            "com.riotgames.league.wildrift",
            "com.king.candycrushsaga",
            "com.kiloo.subwaysurf",
            "com.miniclip.eightballpool",
            "com.nianticlabs.pokemongo"
        ),
        ContentCategory.DATING to setOf(
            "com.tinder",
            "com.bumble.app",
            "co.hinge.app",
            "com.badoo.mobile",
            "com.match.android.matchmobile",
            "com.okcupid.okcupid",
            "com.pof.android",
            "com.grindrapp.android",
            "we.are.herapp",
            "com.coffeemeetsbagel"
        ),
        ContentCategory.NEWS to setOf(
            "com.bbc.news", "com.bbc.mobile.news.ww",
            "com.cnn.mobile.android.phone",
            "com.cricbuzz.android",
            "com.nytimes.android",
            "com.washingtonpost.android",
            "com.aljazeera.net",
            "com.guardian",
            "com.google.android.apps.magazines",
            "com.apple.News",
            "flipboard.app",
            "com.devhd.feedly"
        ),
        ContentCategory.EDUCATION to setOf(
            "com.duolingo",
            "org.khanacademy.android",
            "org.coursera.android",
            "com.udemy.android",
            "org.edx.mobile",
            "ai.brilliant",
            "com.sololearn",
            "com.microblink.photomath",
            "com.wolfram.android.alpha",
            "com.quizlet.quizletandroid",
            "com.ichi2.anki",
            "com.memrise.android.memrise",
            "com.babbel.mobile.android.en",
            "com.rosettastone.mobile",
            "com.codecademy.app"
        ),
        ContentCategory.PRODUCTIVITY to setOf(
            "com.google.android.apps.docs",
            "com.dropbox.android",
            "com.microsoft.skydrive",
            "com.evernote",
            "notion.id",
            "com.trello",
            "com.asana.app",
            "com.todoist",
            "com.anydo",
            "com.ticktick.task",
            "com.google.android.keep",
            "com.microsoft.office.onenote",
            "us.zoom.videomeetings",
            "com.google.android.apps.meetings",
            "com.skype.raider"
        ),
        ContentCategory.SHOPPING to setOf(
            "com.amazon.mShop.android.shopping",
            "com.ebay.mobile",
            "com.alibaba.aliexpresshd",
            "com.walmart.android",
            "com.target.ui",
            "com.bestbuy.android",
            "com.ikea.kompis",
            "com.zzkko",
            "com.amazon.apparel",
            "com.contextlogic.wish",
            "com.wayfair.mobile",
            "com.etsy.android",
            "com.mercari.android",
            "com.offerup.android",
            "com.stockx.android",
            "com.goat.goat"
        ),
        ContentCategory.BROWSERS to setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.apple.mobilesafari",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            "com.UCMobile.intl",
            "org.torproject.torbrowser"
        ),
        ContentCategory.EMAIL to setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android",
            "com.readdle.spark",
            "com.newton.launcher",
            "com.Edmands.edmandsmail",
            "org.kman.AquaMail",
            "com.bluemail.mail"
        ),
        ContentCategory.HEALTH_FITNESS to setOf(
            "com.strava",
            "com.fitbit.FitbitMobile",
            "com.myfitnesspal.android",
            "com.nike.nikeplus",
            "com.nike.plusgps",
            "com.headspace.android",
            "com.calm.android",
            "com.sigmaos.android.insighttimer",
            "com.popularapp.sevenmins",
            "com.ocado.android.sworkit",
            "com.glo.android",
            "com.downdogapp",
            "com.alomoves.android",
            "com.peloton.cyclone",
            "com.flo.health",
            "com.clue.android"
        ),
        ContentCategory.PHOTO_VIDEO to setOf(
            "com.vsco.cam",
            "com.adobe.lrmobile",
            "com.adobe.psmobile",
            "com.niksoftware.snapseed",
            "com.picsart.studio",
            "com.afterlight",
            "com.facetune.app",
            "com.photoroom",
            "com.canva.editor",
            "com.camerasideas.inShot",
            "com.lemon.lvoverseas",
            "com.kinemaster.app",
            "com.adobe.premiererush.videoeditor",
            "com.wondershare.filmorago",
            "com.vivavideo",
            "com.youcut.videoplayer",
            "com.vn.videoeditor"
        ),
        ContentCategory.BOOKS to setOf(
            "com.amazon.kindle",
            "com.audible.application",
            "com.apple.iBooks",
            "com.google.android.apps.books",
            "com.overdrive.mobile.android.libby",
            "com.scribd.app",
            "com.wattpad.android",
            "com.goodreads",
            "com.ideashower.readitlater",
            "com.instapaper.android",
            "com.readwise.reader",
            "com.blinkslabs.blinkist"
        ),
        ContentCategory.FINANCE to setOf(
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.squareup.cash",
            "com.zellepay",
            "com.revolut.revolut",
            "com.transferwise.android",
            "com.binance.dev",
            "com.coinbase.android",
            "com.kraken.trade",
            "io.metamask",
            "com.wallet.crypto.trustapp",
            "com.robinhood.android",
            "com.etoro.openbook",
            "com.acorns.android",
            "com.stashinvest.android",
            "com.mint",
            "com.youneedabudget.evergreen.app",
            "com.creditkarma.mobile",
            "com.turbotax.mobile"
        ),
        ContentCategory.NAVIGATION to setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.apple.Maps",
            "com.here.app.maps",
            "com.mapswithme.maps.me",
            "com.citymapper.app.th",
            "com.tranzmate",
            "com.ubercab",
            "me.lyft.android",
            "com.bolt.android",
            "com.grab.android.passenger",
            "com.careem.acma",
            "com.didiglobal.passenger",
            "com.olacabs.customer",
            "com.rapido.passenger"
        ),
        ContentCategory.FOOD_DRINK to setOf(
            "com.ubercab.eats",
            "com.dd.doordash",
            "com.grubhub.android",
            "com.postmates.android",
            "com.deliveroo.orderapp",
            "com.justeat.android",
            "com.glovo",
            "com.grability.rappi",
            "com.wolt.android",
            "com.foodpanda.android",
            "com.mcdonalds.mobileapp",
            "com.starbucks.mobilecard",
            "com.dominos.android",
            "com.yelp.android",
            "com.opentable"
        ),
        ContentCategory.TRAVEL to setOf(
            "com.airbnb.android",
            "com.booking",
            "com.expedia.bookings",
            "com.hotels.android",
            "com.tripadvisor.android",
            "com.agoda.mobile.consumer",
            "com.kayak.android",
            "net.skyscanner.android.main",
            "com.hopper.mountainhopper",
            "com.trivago.android"
        ),
        ContentCategory.WEATHER to setOf(
            "com.accuweather.android",
            "com.weather.Weather",
            "com.wunderground.android.weather",
            "co.windyapp.android",
            "com.noaa.weather",
            "uk.gov.metoffice.android",
            "com.yahoo.mobile.client.android.weather",
            "com.maccors.todayweather",
            "com.grailr.carrotweather",
            "com.aws.android"
        ),
        ContentCategory.SPORTS to setOf(
            "com.espn.score_center",
            "com.bleacherreport.android.teamstream",
            "com.fivemobile.thescore",
            "com.yahoo.mobile.client.android.sports",
            "com.cbssports.mobile",
            "com.nbcsports.android",
            "com.foxsports.android",
            "bbc.mobile.news.ww.sport",
            "com.livescore",
            "com.sofascore.results",
            "com.flashscore.mobile.android",
            "com.mobilefootie.wc2010",
            "de.motain.iliga"
        ),
        ContentCategory.ANIME_MANGA to setOf(
            "tv.crunchyroll.crunchyroid",
            "com.funimation.mobile",
            "com.animeslayer.android",
            "com.myanimelist",
            "com.lucaslaw.AniList",
            "net.kitsunekod.android",
            "eu.kanade.tachiyomi"
        ),
        ContentCategory.PORN to setOf(
            "com.pornhub.android",
            "com.xvideos",
            "com.xhamster",
            "com.redtube.mobile",
            "com.youporn",
            "com.xnxx",
            "com.tube8",
            "com.brazzers.mobile",
            "com.spankbang",
            "com.beeg",
            "com.hclips",
            "com.txxx",
            "com.chaturbate",
            "com.stripchat.mobile",
            "com.livestream",
            "com.bongaCams"
        ),
        ContentCategory.GAMBLING to setOf(
            "com.bet365.android",
            "com.williamhill.es",
            "com.betway.android",
            "com.x.bet.app",
            "com.fanduel.android",
            "com.draftkings.sportsbook",
            "com.betmgm",
            "com.caesars.sportsbook",
            "com.betrivers",
            "com.pointsbet",
            "com.pokerstars",
            "com.eightyeight.poker"
        ),
        ContentCategory.VPN_PROXY to setOf(
            "com.nordvpn.android",
            "com.expressvpn.vpn",
            "com.surfshark.vpn",
            "com.cyberghostvpn.android",
            "com.privateinternetaccess.android",
            "net.mullvad.mullvadvpn",
            "ch.protonvpn.android",
            "com.windscribe.vpn",
            "com.tunnelbear.android",
            "com.hotspotshield.android"
        ),
        ContentCategory.AI_ASSISTANTS to setOf(
            "com.openai.chatgpt",
            "com.anthropic.claude",
            "com.google.android.apps.bard",
            "com.perplexity.app.android",
            "com.microsoft.copilot",
            "com.poe.android",
            "ai.character.app",
            "com.lukalabs.replika",
            "com.samsung.android.bixby.agent"
        ),
        ContentCategory.KIDS to setOf(
            "com.google.android.apps.youtube.kids",
            "org.pbskids.video",
            "com.nickjr.android",
            "com.disney.datg.videoplatforms.android.apps",
            "com.turner.cnvideoapp",
            "com.lego.legobuildinginstructions",
            "com.mattel.barbie",
            "org.khanacademy.kids",
            "com.abcmouse.abcandroid",
            "com.starfall.android",
            "com.readingeggs.android",
            "com.prodigygame.game",
            "com.dragonbox.duolingo",
            "com.tocaboca.tocalifeworld"
        )
    )

    /**
     * Returns the [ContentCategory] for [packageName], or
     * [ContentCategory.OTHER] if no match. Lookup is O(1) per
     * category — bounded by `map.size`, so always O(N) for a
     * constant N=32.
     */
    fun detect(packageName: String): ContentCategory {
        for ((cat, pkgs) in map) {
            if (packageName in pkgs) return cat
        }
        return ContentCategory.OTHER
    }

    /** Returns the full map for UI consumption (settings screen). */
    fun all(): Map<ContentCategory, Set<String>> = map

    /** Total packages tracked across all categories — used in the
     *  "we auto-detect 250+ apps" marketing string. */
    fun totalPackages(): Int = map.values.sumOf { it.size }
}
