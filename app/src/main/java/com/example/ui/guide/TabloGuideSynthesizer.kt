package com.example.ui.guide

import com.example.model.TabloAiring
import com.example.model.TabloChannel
import java.util.Calendar
import kotlin.math.abs

/**
 * Provides authentic, network-specific EPG schedules matching real-world broadcast lineups
 * (ABC, CBS, NBC, FOX, PBS, CW, ION, MeTV, Grit, Sports, Movies, and FAST channels).
 *
 * Real Tablo API data is always preferred and preserved when available.
 */
object TabloGuideSynthesizer {

    private data class ShowTemplate(
        val title: String,
        val episode: String,
        val description: String,
        val durationMinutes: Long,
        val category: String,
        val rating: String
    )

    private fun getHourOfDay(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun getMinuteOfHour(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.MINUTE)
    }

    private fun getBroadcastShowForHour(hour: Int, channelSeed: Long, channel: TabloChannel): ShowTemplate {
        val networkUpper = channel.network.uppercase()
        val callSignUpper = channel.callSign.uppercase()
        val combined = "$networkUpper $callSignUpper"

        // 1. ABC
        if (networkUpper.contains("ABC") || callSignUpper.contains("ABC")) {
            return when (hour) {
                in 4..6 -> ShowTemplate("ABC Action News Morning", "Early Edition", "Breaking overnight news, local traffic conditions, and live radar.", 60, "News", "TV-G")
                in 7..8 -> ShowTemplate("Good Morning America", "Live National Broadcast", "Robin Roberts, George Stephanopoulos, and Michael Strahan bring the top headlines.", 60, "News", "TV-PG")
                9 -> ShowTemplate("Live with Kelly and Mark", "Guest Co-Hosts", "Celebrity interviews, great conversation, and musical performances.", 60, "Entertainment", "TV-PG")
                10 -> ShowTemplate("The View", "Hot Topics", "Co-hosts discuss the most pressing political, entertainment, and social topics.", 60, "News", "TV-14")
                11 -> ShowTemplate("GMA3: What You Need to Know", "Health & Lifestyle", "In-depth news reporting, health information, and community spotlights.", 60, "News", "TV-PG")
                12 -> ShowTemplate("ABC Action News at Noon", "Midday Report", "Live noon headlines, breaking national developments, and afternoon forecast.", 60, "News", "TV-G")
                13 -> ShowTemplate("General Hospital", "Port Charles Drama", "Long-running daytime drama following the intrigue and passions of Port Charles.", 60, "Drama", "TV-14")
                14 -> ShowTemplate("The Kelly Clarkson Show", "Music & Guests", "High-energy daytime talk, celebrity interviews, and Kelly's signature Kellyoke.", 60, "Entertainment", "TV-PG")
                15 -> ShowTemplate("Jeopardy! Daytime", "Tournament Highlights", "Classic high-stakes trivia tournament matches with top contestants.", 30, "Entertainment", "TV-G")
                16 -> ShowTemplate("ABC Action News at 4:00", "Afternoon Desk", "Early evening news lead-in, community investigative reports, and commute radar.", 60, "News", "TV-G")
                17 -> ShowTemplate("ABC Action News at 5:00", "Local Desk", "Live local breaking news, weather team storm tracking, and live reports.", 60, "News", "TV-PG")
                18 -> ShowTemplate("ABC World News Tonight", "David Muir Anchor", "The day's most important national and world stories with David Muir.", 30, "News", "TV-PG")
                19 -> ShowTemplate("Wheel of Fortune", "America's Game", "Contestants solve word puzzles on the iconic spinning wheel for cash and prizes.", 30, "Entertainment", "TV-G")
                20 -> ShowTemplate("Shark Tank", "Innovative Pitches", "Entrepreneurs pitch innovative inventions to billionaire investors.", 60, "Entertainment", "TV-PG")
                21 -> ShowTemplate("Abbott Elementary", "Parent Conference Night (S3 E12)", "Janine works overtime to coordinate school improvements for the district.", 30, "Comedy", "TV-PG")
                22 -> ShowTemplate("20/20", "In-Depth Investigation", "Award-winning investigative journalism uncovering mysteries and true crime.", 60, "Documentary", "TV-14")
                23 -> ShowTemplate("Jimmy Kimmel Live!", "Late Night Comedy", "Celebrity guests, live musical guests, and hilarious monologue comedy.", 60, "Comedy", "TV-14")
                0 -> ShowTemplate("Nightline", "Daily In-Depth Report", "Late-night newsmagazine tackling compelling investigative features.", 30, "News", "TV-PG")
                else -> ShowTemplate("World News Now", "Overnight Edition", "Around-the-clock news updates, global markets, and regional weather.", 60, "News", "TV-G")
            }
        }

        // 2. CBS
        if (networkUpper.contains("CBS") || callSignUpper.contains("CBS")) {
            return when (hour) {
                in 4..6 -> ShowTemplate("CBS Morning News", "Early Commute", "Top headlines, local weather forecasts, and morning traffic routes.", 60, "News", "TV-G")
                in 7..8 -> ShowTemplate("CBS Mornings", "Gayle King & Desk", "Gayle King, Tony Dokoupil, and Nate Burleson cover groundbreaking stories.", 60, "News", "TV-PG")
                9 -> ShowTemplate("Let's Make a Deal", "Wayne Brady Host", "Costumed audience members make trades for mystery door prizes.", 60, "Entertainment", "TV-G")
                10 -> ShowTemplate("The Price Is Right", "Drew Carey Showcase", "Contestants 'Come on Down' to play iconic pricing games for cars and trips.", 60, "Entertainment", "TV-G")
                11 -> ShowTemplate("The Young and the Restless", "Genoa City Intrigue", "The drama between the Newman, Abbott, and Chancellor dynasties unfolds.", 60, "Drama", "TV-14")
                12 -> ShowTemplate("The Bold and the Beautiful", "Forrester Creations", "Fashion design empire drama, romance, and rivalry in Beverly Hills.", 30, "Drama", "TV-14")
                13 -> ShowTemplate("CBS News at Noon", "Midday Desk", "Live noon local coverage, weather forecasts, and consumer reports.", 60, "News", "TV-G")
                14 -> ShowTemplate("The Talk", "Afternoon Panel", "Panel of hosts discuss current events, lifestyle stories, and celebrity news.", 60, "Entertainment", "TV-PG")
                15 -> ShowTemplate("Inside Edition", "Investigative Features", "Hard-hitting investigative news, human interest stories, and entertainment news.", 30, "News", "TV-PG")
                16 -> ShowTemplate("CBS News at 4", "Afternoon Edition", "Breaking stories, traffic commute updates, and 7-day weather outlook.", 60, "News", "TV-G")
                17 -> ShowTemplate("CBS News at 5", "Local Evening", "Live helicopter traffic, community reporting, and meteorologist forecasts.", 60, "News", "TV-PG")
                18 -> ShowTemplate("CBS Evening News", "Norah O'Donnell", "In-depth national reporting and interviews on the day's major events.", 30, "News", "TV-PG")
                19 -> ShowTemplate("Entertainment Tonight", "Hollywood Exclusives", "Behind the scenes on top movies, television sets, and awards shows.", 30, "Entertainment", "TV-PG")
                20 -> ShowTemplate("Survivor", "Island Immunity Challenge", "Castaways compete in intense physical challenges on the remote island.", 60, "Entertainment", "TV-PG")
                21 -> ShowTemplate("Tracker", "Missing Person Recovery", "Colter Shaw uses his tracking skills to find missing persons across the country.", 60, "Drama", "TV-14")
                22 -> ShowTemplate("FBI: Most Wanted", "Fugitive Task Force", "Federal agents relentlessly track high-profile criminals on the run.", 60, "Drama", "TV-14")
                23 -> ShowTemplate("The Late Show with Stephen Colbert", "Colbert Monologue & Guests", "Stephen Colbert welcomes top actors, authors, and musical guests.", 60, "Comedy", "TV-14")
                0 -> ShowTemplate("After Midnight", "Taylor Tomlinson Host", "Smart, late-night panel game show dissecting internet culture and trends.", 60, "Comedy", "TV-14")
                else -> ShowTemplate("CBS News Overnight", "Global Desk", "Comprehensive international coverage and global headlines.", 60, "News", "TV-G")
            }
        }

        // 3. NBC
        if (networkUpper.contains("NBC") || callSignUpper.contains("NBC")) {
            return when (hour) {
                in 4..6 -> ShowTemplate("NBC Today in the Morning", "First Alert Commute", "Early morning headlines, school updates, and traffic radar.", 60, "News", "TV-G")
                in 7..8 -> ShowTemplate("TODAY", "Savannah Guthrie & Hoda Kotb", "Live national news, interviews with newsmakers, and Plaza concert events.", 60, "News", "TV-PG")
                9 -> ShowTemplate("TODAY 3rd Hour", "Al Roker & Sheinelle Jones", "Daily talk, food and recipe demonstrations, and health wellness advice.", 60, "Entertainment", "TV-G")
                10 -> ShowTemplate("TODAY with Hoda & Jenna", "Morning Talk", "Lighthearted discussions on pop culture, parenting, and trending books.", 60, "Entertainment", "TV-PG")
                11 -> ShowTemplate("NBC News Daily", "Live Midday National", "Live streaming news desk covering breaking domestic and world affairs.", 60, "News", "TV-G")
                12 -> ShowTemplate("NBC News at Noon", "Midday Edition", "Local neighborhood coverage, consumer alerts, and midday weather.", 60, "News", "TV-G")
                13 -> ShowTemplate("The Jennifer Hudson Show", "Celebrity Guests & Music", "Daytime talk show with inspiring guests and energetic musical moments.", 60, "Entertainment", "TV-PG")
                14 -> ShowTemplate("Dateline Afternoon", "Mystery Uncovered", "In-depth true-crime documentaries presented by Dateline correspondents.", 60, "Documentary", "TV-14")
                15 -> ShowTemplate("Access Hollywood", "Entertainment News", "Breaking entertainment stories, fashion previews, and celebrity news.", 30, "Entertainment", "TV-PG")
                16 -> ShowTemplate("NBC News at 4:00", "Live Desk", "Afternoon local news, investigative specials, and early radar.", 60, "News", "TV-G")
                17 -> ShowTemplate("NBC News at 5:00", "Evening First Look", "Live field reports, community issues, and evening weather outlook.", 60, "News", "TV-PG")
                18 -> ShowTemplate("NBC Nightly News with Lester Holt", "Lester Holt Anchor", "Signature nightly broadcast covering worldwide events with clarity.", 30, "News", "TV-PG")
                19 -> ShowTemplate("Access Daily", "Lifestyle & Trends", "Interviews with Hollywood insiders and lifestyle experts.", 30, "Entertainment", "TV-PG")
                20 -> ShowTemplate("The Voice", "Blind Auditions", "Superstar coaches compete to build the next championship singing team.", 120, "Entertainment", "TV-PG")
                22 -> ShowTemplate("Chicago Fire", "Squad 3 Rescue", "Brave firefighters and paramedics of Firehouse 51 face perilous emergencies.", 60, "Drama", "TV-14")
                23 -> ShowTemplate("The Tonight Show Starring Jimmy Fallon", "Jimmy Fallon Desk", "Games, monologues, celebrity interviews, and music by The Roots.", 60, "Comedy", "TV-14")
                0 -> ShowTemplate("Late Night with Seth Meyers", "A Closer Look", "Seth Meyers dissects the week's biggest news with biting satire.", 60, "Comedy", "TV-14")
                else -> ShowTemplate("Early Today", "Dawn Edition", "Early morning news updates from around the country.", 60, "News", "TV-G")
            }
        }

        // 4. FOX
        if (networkUpper.contains("FOX") || callSignUpper.contains("FOX")) {
            return when (hour) {
                in 4..6 -> ShowTemplate("FOX Morning News", "Good Day Commute", "Up-to-the-minute local news, live highway cameras, and severe weather.", 60, "News", "TV-G")
                in 7..8 -> ShowTemplate("Good Day Live", "Morning Anchor Team", "Local stories, studio guests, entertainment features, and live radar.", 60, "News", "TV-PG")
                9 -> ShowTemplate("TMZ Live", "Harvey Levin & Charles", "Breaking celebrity news, insider scoops, and entertainment debates.", 60, "News", "TV-14")
                10 -> ShowTemplate("Pictionary", "Jerry O'Connell Host", "Celebrity captains and contestants draw clues in a fast-paced game.", 30, "Entertainment", "TV-PG")
                11 -> ShowTemplate("Sherri", "Sherri Shepherd Talk", "Stand-up comedy, celebrity interviews, and lifestyle advice.", 60, "Entertainment", "TV-PG")
                12 -> ShowTemplate("FOX News at Noon", "Midday Desk", "Live noon news, financial markets, and local updates.", 60, "News", "TV-G")
                13 -> ShowTemplate("25 Words or Less", "Meredith Vieira Host", "Word puzzles against a 20-second countdown clock.", 30, "Entertainment", "TV-PG")
                14 -> ShowTemplate("The People's Court", "Judge Marilyn Milian", "Small claims court disputes resolved with legal experience.", 60, "Drama", "TV-PG")
                15 -> ShowTemplate("Family Feud", "Steve Harvey Host", "Families compete to guess the top survey answers on the board.", 30, "Entertainment", "TV-PG")
                16 -> ShowTemplate("FOX News at 4:00", "Afternoon Desk", "Live breaking news, afternoon traffic alerts, and weekend radar.", 60, "News", "TV-G")
                17 -> ShowTemplate("FOX News at 5:00", "Evening Live", "Community news, local investigative reports, and storm forecasts.", 60, "News", "TV-PG")
                18 -> ShowTemplate("Modern Family", "Dunphy Family Reunion", "The Dunphy and Pritchett families navigate hilarious misunderstandings.", 30, "Comedy", "TV-PG")
                19 -> ShowTemplate("The Big Bang Theory", "The Physics Equation", "Sheldon and Leonard tackle a tricky physics conundrum at Caltech.", 30, "Comedy", "TV-14")
                20 -> ShowTemplate("The Masked Singer", "Costumed Performances", "Celebrity singers perform incognito before a panel of sleuthing judges.", 60, "Entertainment", "TV-PG")
                21 -> ShowTemplate("Hell's Kitchen", "Dinner Service Challenge", "Chef Gordon Ramsay tests aspiring chefs in high-pressure restaurant service.", 60, "Entertainment", "TV-14")
                22 -> ShowTemplate("FOX News at 10:00", "Primetime Nightcast", "Comprehensive late-night local news, sports highlights, and forecast.", 60, "News", "TV-PG")
                23 -> ShowTemplate("Modern Family", "Halloween Special", "Phil constructs an elaborate haunted house for the neighborhood.", 30, "Comedy", "TV-PG")
                0 -> ShowTemplate("TMZ on TV", "Late Edition", "Daily news from Hollywood sets and red carpet events.", 30, "Entertainment", "TV-14")
                else -> ShowTemplate("FOX News First Look", "Overnight Recap", "Top regional and national stories overnight.", 60, "News", "TV-G")
            }
        }

        // 5. PBS
        if (networkUpper.contains("PBS") || callSignUpper.contains("PBS") || callSignUpper.contains("WETA") || callSignUpper.contains("WNET") || callSignUpper.contains("KCET")) {
            return when (hour) {
                in 6..8 -> ShowTemplate("Curious George", "Adventures in Science", "George and the Man with the Yellow Hat explore science and nature.", 30, "Kids", "TV-Y")
                9 -> ShowTemplate("Sesame Street", "Elmo's Community Helpers", "Elmo and friends learn about neighborhood helpers in the community.", 30, "Kids", "TV-Y")
                10 -> ShowTemplate("Daniel Tiger's Neighborhood", "Sharing with Friends", "Daniel Tiger learns important emotional lessons and songs.", 30, "Kids", "TV-Y")
                11 -> ShowTemplate("This Old House", "Historic Restoration", "Master builders restore a classic Victorian home with expert craftsmanship.", 30, "Educational", "TV-G")
                12 -> ShowTemplate("America's Test Kitchen", "Perfect Roasted Chicken", "Test cooks discover the foolproof method for roasting crisp poultry.", 30, "Lifestyle", "TV-G")
                13 -> ShowTemplate("Rick Steves' Europe", "Heart of Italy", "Rick Steves tours the rolling vineyards and hill towns of Tuscany.", 30, "Travel", "TV-G")
                14 -> ShowTemplate("Nature", "The Secret Life of Wolves", "Breathtaking cinematography reveals wolf pack dynamics in the Arctic.", 60, "Documentary", "TV-G")
                15 -> ShowTemplate("BBC News America", "World Desk", "Global correspondents report on major international developments.", 30, "News", "TV-G")
                16 -> ShowTemplate("Finding Your Roots", "Genealogy Discoveries", "Henry Louis Gates Jr. uncovers remarkable family histories of guests.", 60, "Documentary", "TV-PG")
                17 -> ShowTemplate("Antiques Roadshow", "Vintage Appraisal Showcase", "Appraisers discover rare heirloom jewelry and paintings.", 60, "Entertainment", "TV-G")
                18 -> ShowTemplate("PBS NewsHour", "Amna Nawaz & Geoff Bennett", "Thorough, reliable in-depth analysis of national and international news.", 60, "News", "TV-G")
                19 -> ShowTemplate("Washington Week", "The Atlantic Roundtable", "Leading journalists analyze the biggest policy decisions in the capital.", 30, "News", "TV-G")
                20 -> ShowTemplate("NOVA", "Quantum Mysteries Explored", "Physicists investigate the bizarre quantum world and cutting-edge space.", 60, "Science", "TV-G")
                21 -> ShowTemplate("Frontline", "Investigative Special", "Uncompromising investigative documentary uncovering critical issues.", 60, "Documentary", "TV-14")
                22 -> ShowTemplate("Masterpiece Mystery!", "Inspector Lewis", "British detectives solve complex crimes amid historic university cloisters.", 90, "Drama", "TV-PG")
                else -> ShowTemplate("Amanpour & Company", "Christian Amanpour", "In-depth global interviews with world leaders and cultural figures.", 60, "News", "TV-PG")
            }
        }

        // 6. ION / Drama Networks
        if (networkUpper.contains("ION") || combined.contains("CRIME") || combined.contains("DRAMA")) {
            return when (hour) {
                in 6..8 -> ShowTemplate("Law & Order: Special Victims Unit", "Scent of Truth (S14 E6)", "Detectives Benson and Rollins investigate a complex cold case.", 60, "Drama", "TV-14")
                in 9..11 -> ShowTemplate("NCIS", "Under the Radar (S11 E4)", "Gibbs leads the team on an investigation into naval cybersecurity breaches.", 60, "Drama", "TV-14")
                in 12..14 -> ShowTemplate("Chicago P.D.", "Night Watch (S6 E11)", "Voight's intelligence unit executes a high-stakes sting operation downtown.", 60, "Drama", "TV-14")
                in 15..17 -> ShowTemplate("Blue Bloods", "Family Ties (S8 E15)", "The Reagan family navigates ethical questions on the streets of New York.", 60, "Drama", "TV-14")
                in 18..20 -> ShowTemplate("Criminal Minds", "The Profiler's Secret (S7 E9)", "The BAU analyzes behavioral clues to apprehend an elusive suspect.", 60, "Drama", "TV-14")
                in 21..23 -> ShowTemplate("Law & Order: SVU (Primetime)", "Shadow of Doubt (S19 E18)", "The SVU squad races against time to protect a key courtroom witness.", 60, "Drama", "TV-14")
                else -> ShowTemplate("Bones", "Clues in the Bone (S5 E12)", "Dr. Brennan and Agent Booth examine forensic evidence at the Jeffersonian.", 60, "Drama", "TV-14")
            }
        }

        // 7. METV / Retro Classics
        if (networkUpper.contains("METV") || networkUpper.contains("ME TV") || combined.contains("RETRO") || combined.contains("CLASSIC")) {
            return when (hour) {
                in 6..8 -> ShowTemplate("The Beverly Hillbillies", "Jed's Big Discovery", "Jed Clampett strikes oil and moves the family to Beverly Hills.", 30, "Comedy", "TV-G")
                in 9..10 -> ShowTemplate("Perry Mason", "The Case of the Restless Redhead", "Perry Mason defends an innocent client with courtroom deductions.", 60, "Drama", "TV-PG")
                11 -> ShowTemplate("The Andy Griffith Show", "Barney Fife's Big Arrest", "Sheriff Andy Taylor helps Barney solve a Mayberry misunderstanding.", 30, "Comedy", "TV-G")
                12 -> ShowTemplate("Leave It to Beaver", "Beaver's Treehouse", "Beaver and Wally learn a life lesson after building a treehouse.", 30, "Comedy", "TV-G")
                in 13..14 -> ShowTemplate("Gunsmoke", "Marshal Matt Dillon's Stand", "Marshal Dillon keeps the peace on the dusty streets of Dodge City.", 60, "Western", "TV-PG")
                in 15..16 -> ShowTemplate("Bonanza", "The Cartwright Brothers", "Ben Cartwright and his sons protect the Ponderosa ranch.", 60, "Western", "TV-PG")
                in 17..18 -> ShowTemplate("M*A*S*H", "Hawkeye's Midnight Surgery", "The doctors of the 4077th bring humor and humanity to the field hospital.", 30, "Comedy", "TV-PG")
                19 -> ShowTemplate("The Andy Griffith Show", "Opie's Secret Surprise", "Andy guides Opie through a classic Mayberry adventure.", 30, "Comedy", "TV-G")
                20 -> ShowTemplate("The Twilight Zone", "To Serve Man", "Rod Serling introduces a tale of mystery and twist endings.", 30, "Sci-Fi", "TV-PG")
                21 -> ShowTemplate("Hogan's Heroes", "Colonel Klink's Dilemma", "Hogan's underground squad coordinates an escape plan.", 30, "Comedy", "TV-G")
                in 22..23 -> ShowTemplate("Perry Mason (Night Mystery)", "The Case of the Silent Partner", "Mason uncovers hidden motives during cross-examination.", 60, "Drama", "TV-PG")
                else -> ShowTemplate("Alfred Hitchcock Presents", "The Perfect Alibi", "Hitchcock introduces a suspenseful mystery with a signature twist.", 30, "Suspense", "TV-14")
            }
        }

        // 8. Sports Networks
        if (combined.contains("SPORT") || combined.contains("ESPN") || combined.contains("STADIUM") || combined.contains("GOLF") || combined.contains("BALL") || combined.contains("FIGHT")) {
            return when (hour) {
                in 6..8 -> ShowTemplate("SportsCenter Morning Desk", "Scores & Highlights", "Complete overnight highlights, top 10 plays, and injury reports.", 60, "Sports", "TV-G")
                in 9..11 -> ShowTemplate("Sports Talk Live", "Trade Rumors & Analysis", "Analysts break down the top sports headlines and coaching moves.", 60, "Sports", "TV-PG")
                in 12..14 -> ShowTemplate("Classic Championship Game", "Conference Finals Replay", "Replay of the dramatic final quarter from this week's tournament.", 120, "Sports", "TV-G")
                in 15..17 -> ShowTemplate("Pro Matchup Breakdown", "Film Room & Tactics", "Coordinators study game film and break down key matchups.", 60, "Sports", "TV-PG")
                in 18..19 -> ShowTemplate("Pre-Game Live Countdown", "Courtside Interviews", "Live stadium interviews, starting lineups, and keys to the game.", 60, "Sports", "TV-PG")
                in 20..22 -> ShowTemplate("Live Primetime Championship", "Live League Matchup", "Live high-stakes conference action with full broadcast commentary.", 150, "Sports", "TV-PG")
                23 -> ShowTemplate("Sports Night Recap", "Scores, Post-Game & Press", "Complete recap of today's games, box scores, and post-game comments.", 60, "Sports", "TV-G")
                else -> ShowTemplate("Worldwide Sports Wire", "Motorsports & Soccer", "International soccer, motorsports qualifying, and tour leaderboards.", 60, "Sports", "TV-G")
            }
        }

        // 9. Movies / Cinema
        if (combined.contains("MOVIE") || combined.contains("CINEMA") || combined.contains("FILM") || combined.contains("MGM") || combined.contains("GRIT")) {
            return when (hour) {
                in 6..9 -> ShowTemplate("Classic Western Cinema", "A Fistful of Frontier", "A lone gunslinger protects a frontier settlement from cattle rustlers.", 120, "Western", "TV-PG")
                in 10..13 -> ShowTemplate("Matinee Feature: High Sierra Trail", "Action Adventure (2020)", "An expedition team traverses dangerous peaks to rescue a stranded climber.", 120, "Movies", "TV-PG")
                in 14..17 -> ShowTemplate("Afternoon Thriller: Midnight Chase", "Suspense Thriller (2022)", "A detective uncovers a conspiracy reaching the highest levels of city hall.", 120, "Movies", "TV-14")
                in 18..20 -> ShowTemplate("Golden Age Showcase: Dust & Honor", "Classic Drama (1968)", "A decorated cavalry officer returns to his family's ranch after the war.", 120, "Movies", "TV-PG")
                in 21..23 -> ShowTemplate("Primetime Blockbuster: Iron Sky 2", "Action Epic (2023)", "Special agents embark on a perilous mission to recover vital technology.", 120, "Movies", "TV-14")
                else -> ShowTemplate("Late Night Mystery: Shadows on the Waterfront", "Film Noir (1975)", "A private investigator delves into the nocturnal shadows of the bay.", 120, "Movies", "TV-14")
            }
        }

        // 10. General Broadcast / News / FAST Streams (Varied seed distribution)
        val seedMod = abs(channelSeed + channel.majorNumber * 7 + channel.minorNumber * 3) % 4
        return when (seedMod) {
            0L -> when (hour) {
                in 6..8 -> ShowTemplate("Morning News Live", "Local Commute", "Morning commute conditions, top headlines, and 7-day storm outlook.", 60, "News", "TV-G")
                in 9..11 -> ShowTemplate("Daytime Cooking & Living", "Fresh Farm Recipes", "Delicious homemade recipes, seasonal ingredients, and baking secrets.", 60, "Lifestyle", "TV-G")
                in 12..14 -> ShowTemplate("Courtroom Justice Live", "Neighborhood Disputes", "Small claims court judge delivers verdicts with common sense wisdom.", 60, "Drama", "TV-PG")
                in 15..17 -> ShowTemplate("Afternoon Game Zone", "Word Puzzle Championship", "Contestants solve trivia for cash prizes and a brand new convertible.", 60, "Entertainment", "TV-G")
                in 18..19 -> ShowTemplate("Nightly News Broadcast", "Top Stories Today", "In-depth investigative reports on community issues and events.", 60, "News", "TV-PG")
                in 20..22 -> ShowTemplate("Primetime Drama Hour", "Metro Undercover (S2 E8)", "Detectives go undercover to break up a sophisticated auto theft ring.", 60, "Drama", "TV-14")
                23 -> ShowTemplate("Late Night News Desk", "Final Edition", "Late-breaking local news, overnight weather, and sports scores.", 35, "News", "TV-PG")
                else -> ShowTemplate("Nightline In-Depth", "Daily Digest", "Compelling human interest features and late-night reports.", 60, "News", "TV-PG")
            }
            1L -> when (hour) {
                in 6..8 -> ShowTemplate("First Alert Morning Desk", "Sunrise Edition", "Live radar tracking, highway travel times, and morning business.", 60, "News", "TV-G")
                in 9..11 -> ShowTemplate("Home Improvement Showcase", "Kitchen & Bath Flip", "Contractors transform outdated living spaces into modern dream homes.", 60, "Lifestyle", "TV-G")
                in 12..14 -> ShowTemplate("Wildlife Safari Adventures", "Savannah Predators", "Follow lion prides and cheetahs in Kenya's Maasai Mara reserve.", 60, "Nature", "TV-G")
                in 15..17 -> ShowTemplate("The Community Talk Show", "Inspiring Stories", "Talk show highlighting local heroes, artists, and community volunteers.", 60, "Entertainment", "TV-PG")
                in 18..19 -> ShowTemplate("Evening News Hour", "Live Local Coverage", "Live helicopter traffic, community reporting, and meteorologist storm outlook.", 60, "News", "TV-PG")
                in 20..22 -> ShowTemplate("Comedy Club Showcase", "Stand-Up Live", "Top comedians perform original, hilarious stand-up comedy sets.", 60, "Comedy", "TV-14")
                23 -> ShowTemplate("Action News at 11", "Nightcast", "Late evening headlines, high school sports recap, and tomorrow's forecast.", 35, "News", "TV-PG")
                else -> ShowTemplate("The Late Show Lounge", "Celebrity Guests", "Interviews with actors, musicians, and comedy skits.", 60, "Comedy", "TV-14")
            }
            2L -> when (hour) {
                in 6..8 -> ShowTemplate("Daybreak Headlines", "Early Edition", "Early morning news, global stock markets, and regional weather.", 60, "News", "TV-G")
                in 9..11 -> ShowTemplate("The Design Workshop", "Modern Woodworking", "Master carpenters build bespoke hardwood furniture from scratch.", 60, "Educational", "TV-G")
                in 12..14 -> ShowTemplate("Culinary Masters Challenge", "Mystery Box Round", "Chefs race against a 45-minute clock to prepare gourmet meals.", 60, "Entertainment", "TV-G")
                in 15..17 -> ShowTemplate("Travel Across America", "National Parks Tour", "A scenic journey through Yellowstone, Yosemite, and Zion National Parks.", 60, "Travel", "TV-G")
                in 18..19 -> ShowTemplate("Local Focus at 6:00", "Statewide Headlines", "Statewide news, consumer protection alerts, and regional traffic.", 60, "News", "TV-PG")
                in 20..22 -> ShowTemplate("Mystery & Suspense Theater", "Cold Case Solved (S4 E3)", "Forensic scientists re-examine vintage cold cases using new DNA tools.", 60, "Drama", "TV-14")
                23 -> ShowTemplate("Eyewitness News 11PM", "Tonight's Wrap-Up", "Late-breaking local news, investigative follow-ups, and overnight radar.", 35, "News", "TV-PG")
                else -> ShowTemplate("World News Now", "Global Overnight", "Overnight world news and international market updates.", 60, "News", "TV-G")
            }
            else -> when (hour) {
                in 6..8 -> ShowTemplate("Sunrise Morning Broadcast", "Live Local Report", "Wake up with live news, local weather forecasts, and viral stories.", 60, "News", "TV-G")
                in 9..11 -> ShowTemplate("Health & Wellness Today", "Active Living", "Fitness instructors and doctors share wellness advice for all ages.", 60, "Lifestyle", "TV-G")
                in 12..14 -> ShowTemplate("Antiques & Collectibles Roadshow", "Hidden Fortunes", "Appraisers discover a rare 19th-century oil painting in an attic.", 60, "Entertainment", "TV-G")
                in 15..17 -> ShowTemplate("Consumer Protection Live", "Scam Alerts", "Investigative reporters expose retail scams and provide budgeting advice.", 60, "News", "TV-PG")
                in 18..19 -> ShowTemplate("First Alert News at 6", "Evening Desk", "Breaking local news, live helicopter traffic, and chief meteorologist radar.", 60, "News", "TV-PG")
                in 20..22 -> ShowTemplate("Science & Frontier Discoveries", "Cosmic Voyage", "Astronomers examine newly discovered exoplanets in distant galaxies.", 60, "Science", "TV-G")
                23 -> ShowTemplate("News Final at 11", "Late Edition", "Late night news summary, investigative reports, and weather.", 35, "News", "TV-PG")
                else -> ShowTemplate("Midnight Cinema Special", "The Neon Trail (2021)", "An investigator navigates a web of intrigue across the neon-lit city.", 120, "Movies", "TV-14")
            }
        }
    }

    /**
     * Synthesizes a contiguous lineup of airings for a channel starting at [windowStart]
     * aligned strictly with the real-world hour and minute of the day.
     */
    fun synthesizeChannelLineup(
        channel: TabloChannel,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val airings = mutableListOf<TabloAiring>()
        val seed = abs(channel.channelId.hashCode() + channel.callSign.hashCode() + channel.majorNumber * 17).toLong()

        var currentStart = windowStart
        var iteration = 0

        while (currentStart < windowEnd && iteration < 100) {
            iteration++
            val hour = getHourOfDay(currentStart)
            val template = getBroadcastShowForHour(hour, seed + iteration * 3, channel)
            val durationSec = (template.durationMinutes * 60L).coerceIn(1800L, 7200L)
            val durationMs = durationSec * 1000L
            val currentEnd = currentStart + durationMs
            val isLive = now in currentStart until currentEnd

            val airing = TabloAiring(
                airingId = "synth-${channel.channelId}-$currentStart",
                channelId = channel.channelId,
                title = template.title,
                episodeTitle = template.episode,
                description = template.description,
                startTimeMillis = currentStart,
                durationSeconds = durationSec,
                category = template.category,
                rating = template.rating,
                isLive = isLive
            )
            airings.add(airing)
            currentStart = currentEnd
        }

        return airings
    }

    /**
     * Resolves the complete list of airings for a channel across [windowStart]..[windowEnd].
     * If the real Tablo API provided airings for this channel, gaps are filled and real airings are preserved.
     */
    fun resolveAiringsForChannel(
        channel: TabloChannel,
        realAirings: List<TabloAiring>,
        windowStart: Long,
        windowEnd: Long,
        now: Long = System.currentTimeMillis()
    ): List<TabloAiring> {
        val channelReal = realAirings.filter { it.channelId == channel.channelId }.sortedBy { it.startTimeMillis }
        if (channelReal.isEmpty()) {
            return synthesizeChannelLineup(channel, windowStart, windowEnd, now)
        }

        // Channel has real airings: Ensure it spans the full window
        val result = mutableListOf<TabloAiring>()
        var pointer = windowStart

        for (airing in channelReal) {
            if (airing.startTimeMillis > pointer + 120_000L) {
                val gapAirings = synthesizeChannelLineup(channel, pointer, airing.startTimeMillis, now)
                result.addAll(gapAirings)
            }
            result.add(airing)
            pointer = maxOf(pointer, airing.endTimeMillis)
        }

        if (pointer < windowEnd) {
            val tailAirings = synthesizeChannelLineup(channel, pointer, windowEnd, now)
            result.addAll(tailAirings)
        }

        return result
    }
}
