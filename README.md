# Gemini News - AI-Powered News Aggregator

**Gemini News** is a cutting-edge Android application that transforms the news reading experience using **Google Gemini AI**. It doesn't just aggregate news; it understands it, summarizes it, and personalizes it for you.

## ✨ Core AI Features

- **Smart Summaries**: Get the "Key Takeaways," "Sentiment," and "Key Entities" for any article with a single tap.
- **Reading Mode**: AI-cleaned, distraction-free reader that extracts the core story and formats it beautifully in Markdown.
- **AI News Chat**: Chat directly with a News Expert (Gemini) about today's headlines or specific stories. Gemini is automatically "briefed" on current events.
- **News Map**: Visualize the news. AI identifies locations in headlines and plots them on an interactive global map.
- **Smart Categories**: Dynamic re-categorization. AI identifies timely themes (like "AI Breakthroughs" or "Market Shifts") beyond standard API labels.
- **For You Feed**: Highly personalized recommendations based on an AI analysis of your reading history and bookmarks.
- **Trending Topics**: One-tap analysis of the top 5 global narratives happening right now.
- **Reading Stats**: Get a "Weekly News Digest" with a creative "News Personality" and personalized recommendations based on your habits.
- **AI Translation**: Full support for summarizing and reading news in your preferred language.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with StateFlow
- **AI Model**: `gemini-flash-latest` via Firebase Vertex AI
- **Persistence**: Room (Bookmarks, History, Offline Cache)
- **Networking**: Retrofit & Kotlinx Serialization
- **Background**: WorkManager (Breaking News & Reading Reminders)
- **Navigation**: Material 3 Adaptive (ListDetailPaneScaffold)
- **Media**: Coil (Async Images) & Text-To-Speech (Read Aloud)
- **Deep Linking**: Full support for category and search deep links.

## 📱 Features & UX

- **Adaptive Layout**: Optimized for Phones, Tablets, and Foldables with a professional two-pane experience.
- **Voice Search**: Search for news naturally using your voice.
- **Read Aloud**: Listen to articles in Reading Mode with high-quality Text-To-Speech.
- **Breaking News**: Background notifications for high-priority headlines.
- **Save for Later**: Intelligent daily reminders for your bookmarked stories.
- **Shimmer Effects**: Premium skeleton loading for a smooth, perceived performance.
- **Deep Links & Shortcuts**: Jump straight to your favorite categories from the home screen.

## 🚀 Setup

1.  **NewsAPI**: Obtain an API key from [newsapi.org](https://newsapi.org/) and add it to your git-ignored `local.properties` file:
    `NEWS_API_KEY=your_key_here` (it is exposed to the app as `BuildConfig.NEWS_API_KEY` — never commit it).
    A pre-commit hook blocks accidental secret commits; in fresh clones enable it with
    `git config core.hooksPath .githooks`.
2.  **Google Maps**: Add your Google Maps API Key to your git-ignored `local.properties` file as `MAPS_API_KEY=your_key_here` (it is injected into `AndroidManifest.xml` as `${GOOGLE_MAPS_API_KEY}` at build time — never commit it).
3.  **Firebase**: Connect your project to Firebase and enable **Vertex AI for Firebase**.

---
*Built with ❤️ and Gemini AI.*
