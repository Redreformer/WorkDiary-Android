# WorkDiary Android — Conversion Progress

## Team
| Agent | Role | Model |
|-------|------|-------|
| Main (Claude) | Director, planning, review | claude-sonnet-4-6 |
| MrsWillow (Gemini) | Comparison, research, validation | gemini-3.1-pro |
| MrWillow (ChatGPT) | Kotlin + Jetpack Compose generation | gpt-5.1-codex |
| RedReformer (Ollama) | File analysis, extraction, low-cost processing | llama3.1:8b |
| RedWillow (Groq) | Fast helper tasks, summaries, quick rewrites | llama-3.3-70b |

## Conversion Order
1. [ ] storage/helpers (KeyGen, CalendarManager)
2. [ ] models (Holiday, ZoomItem)
3. [ ] navigation shell (ContentView, AnnualLeaveTrackerApp)
4. [ ] onboarding (OnboardingView, SplashScreenView)
5. [ ] settings (SettingsView)
6. [ ] balance (BookedView, DashboardView)
7. [ ] holidays (Holiday model + views)
8. [ ] calendar cells (CalendarDayCell)
9. [ ] calendar screen (CalendarView)
10. [ ] OCR / PDF / photo (PDFManager, ZoomableImageView, ZoomableScrollView, ZoomItem)

## File Status

| Swift File | Category | Status | Android Output | Notes |
|-----------|----------|--------|----------------|-------|
| KeyGen.swift | storage | ⏳ pending | | |
| CalendarManager.swift | storage/helper | ⏳ pending | | |
| Holiday.swift | model | ⏳ pending | | |
| ZoomItem.swift | model | ⏳ pending | | |
| AnnualLeaveTrackerApp.swift | navigation | ⏳ pending | | |
| ContentView.swift | navigation | ⏳ pending | | |
| OnboardingView.swift | onboarding | ⏳ pending | | |
| SplashScreenView.swift | onboarding | ⏳ pending | | |
| SettingsView.swift | settings | ⏳ pending | | |
| BookedView.swift | balance | ⏳ pending | | |
| DashboardView.swift | balance | ⏳ pending | | |
| CalendarDayCell..swift | calendar | ✅ done | ui/components/CalendarDayCell.kt, WeekRowCell.kt, DayHeaderCell.kt, EmptyMonthCell.kt | Phase 8 – 2026-03-09 |
| CalendarView.swift | calendar | ✅ done | ui/screens/CalendarScreen.kt, CalendarViewModel.kt, NoteEditorSheet.kt | Phase 9 – 2026-03-10 |
| PDFManager.swift | OCR/PDF | ✅ done | utils/PDFManager.kt | Phase 10 – 2026-03-10 |
| ZoomableImageView.swift | OCR/PDF | ✅ done | ui/screens/ZoomImageScreen.kt | Phase 10 – 2026-03-10 |
| ZoomableScrollView.swift | OCR/PDF | ✅ done | ui/screens/ZoomImageScreen.kt (ZoomablePhoto composable) | Phase 10 – 2026-03-10 |
| ShiftButton.swift | component | ⏳ pending | | |
| QuickShiftButton.swift | component | ⏳ pending | | |
| SingleDayNoteEditor.swift | component | ⏳ pending | | |
| DutyIntents.swift | special | ⏳ pending | | |

## Blockers
_None yet_

## Log
- 2026-03-09: Project initialised. GitHub repo created. Android skeleton built. Analysis starting.
- 2026-03-09: Phase 8 complete. Calendar cell composables generated: CalendarDayCell, WeekRowCell, DayHeaderCell, EmptyMonthCell. Committed to GitHub (250081e). Ready for Phase 9 (CalendarScreen).
- 2026-03-10: Phase 9 complete. Main calendar screen implemented: CalendarScreen.kt (Day/Week/Month HorizontalPager views, DetailArea, QuickShiftGrid, PatternGeneratorSheet), CalendarViewModel.kt (Hilt, full note CRUD, shift assign, pattern generator, photo file-path helper), NoteEditorSheet.kt. NavGraph wired to CalendarScreen. Phase 10 TODO hooks in place for OCR/photo capture.
- 2026-03-10: Phase 10 complete. OCR / PDF / Photos implemented: DutyScanner.kt (ML Kit OCR, mirrors Apple Vision DutyScanner), PDFManager.kt (PdfiumCore render + OCR text search, mirrors PDFManager.swift), PhotoManager.kt (file I/O, naming, FileProvider URIs, camera temp file), ZoomImageScreen.kt (full-screen zoomable pager with pinch/double-tap/delete, mirrors ZoomableImageView+ZoomableScrollView), PhotoManagerProvider.kt (Hilt EntryPoint for Compose), CalendarScreen.kt updated (DayPageContent wired with real photo thumbnails + AddPhotoButton with camera/gallery/files/duty-search), CalendarViewModel.kt updated (photo zoom state, savePhotoAndScan, deletePhoto, renderDutyToSlot3), build.gradle.kts updated (ML Kit text-recognition:16.0.0).
