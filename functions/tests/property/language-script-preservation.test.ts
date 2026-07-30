/**
 * Property-based tests for Language and Script Preservation.
 *
 * Property 6: Language and script preservation
 * For inputs with non-Latin scripts (Devanagari, Arabic, CJK), verify output
 * preserves original script without transliteration.
 *
 * The system contract is: Parser_V2 SHALL preserve the original language and script
 * of the task title and description in the structured output without translating
 * or transliterating. The StructuredOutputValidator must accept non-Latin characters
 * and the validated output must contain the exact same characters as the input.
 *
 * **Validates: Requirements 2.4**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateParseResponse } from "../../src/v2/services/structured-output-validator";

// ─── Non-Latin Script Character Pools ──────────────────────────────────────────

/**
 * Devanagari script characters (Hindi and other Indic languages).
 * Range: U+0900–U+097F
 */
const DEVANAGARI_CHARS = [
  "अ", "आ", "इ", "ई", "उ", "ऊ", "ए", "ऐ", "ओ", "औ",
  "क", "ख", "ग", "घ", "च", "छ", "ज", "झ", "ट", "ठ",
  "ड", "ढ", "ण", "त", "थ", "द", "ध", "न", "प", "फ",
  "ब", "भ", "म", "य", "र", "ल", "व", "श", "ष", "स", "ह",
  "ा", "ि", "ी", "ु", "ू", "े", "ै", "ो", "ौ", "्",
];

/**
 * Arabic script characters.
 * Range: U+0600–U+06FF
 */
const ARABIC_CHARS = [
  "ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر",
  "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف",
  "ق", "ك", "ل", "م", "ن", "ه", "و", "ي", "ة", "ء",
  "آ", "أ", "إ", "ؤ", "ئ",
];

/**
 * CJK Unified Ideographs (Chinese/Japanese Kanji).
 * Range: U+4E00–U+9FFF (subset)
 */
const CJK_CHARS = [
  "你", "好", "世", "界", "学", "习", "工", "作", "任", "务",
  "完", "成", "今", "天", "明", "日", "时", "间", "事", "情",
  "计", "划", "目", "标", "会", "议", "电", "话", "文", "件",
  "重", "要", "紧", "急", "项", "目", "报", "告", "邮", "件",
];

/**
 * Japanese Hiragana characters.
 * Range: U+3040–U+309F
 */
const HIRAGANA_CHARS = [
  "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ",
  "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と",
  "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ",
  "ま", "み", "む", "め", "も", "や", "ゆ", "よ",
];

/**
 * Korean Hangul characters.
 * Range: U+AC00–U+D7AF (subset)
 */
const KOREAN_CHARS = [
  "가", "나", "다", "라", "마", "바", "사", "아", "자", "차",
  "카", "타", "파", "하", "할", "일", "오", "늘", "내", "일",
  "작", "업", "완", "료", "시", "간", "중", "요", "긴", "급",
];

// ─── Generators ────────────────────────────────────────────────────────────────

/**
 * Generates a string composed entirely of Devanagari characters.
 */
const arbDevanagariString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(fc.constantFrom(...DEVANAGARI_CHARS), { minLength: minLen, maxLength: maxLen })
    .map((chars) => chars.join(""));

/**
 * Generates a string composed entirely of Arabic characters.
 */
const arbArabicString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(fc.constantFrom(...ARABIC_CHARS), { minLength: minLen, maxLength: maxLen })
    .map((chars) => chars.join(""));

/**
 * Generates a string composed entirely of CJK characters.
 */
const arbCJKString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(fc.constantFrom(...CJK_CHARS), { minLength: minLen, maxLength: maxLen })
    .map((chars) => chars.join(""));

/**
 * Generates a string composed entirely of Hiragana characters.
 */
const arbHiraganaString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(fc.constantFrom(...HIRAGANA_CHARS), { minLength: minLen, maxLength: maxLen })
    .map((chars) => chars.join(""));

/**
 * Generates a string composed entirely of Korean Hangul characters.
 */
const arbKoreanString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(fc.constantFrom(...KOREAN_CHARS), { minLength: minLen, maxLength: maxLen })
    .map((chars) => chars.join(""));

/**
 * Generates a non-Latin script string from any of the supported scripts.
 */
const arbNonLatinString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc.oneof(
    arbDevanagariString(minLen, maxLen),
    arbArabicString(minLen, maxLen),
    arbCJKString(minLen, maxLen),
    arbHiraganaString(minLen, maxLen),
    arbKoreanString(minLen, maxLen)
  );

/**
 * Generates a valid parse response with non-Latin title.
 */
const arbNonLatinParseResponse = (titleArb: fc.Arbitrary<string>) =>
  fc.record(
    {
      title: titleArb,
      confidence: fc.double({ min: 0, max: 1, noNaN: true }),
      description: fc.option(arbNonLatinString(1, 100), { nil: undefined }),
      detectedLanguage: fc.option(
        fc.constantFrom("hi", "ar", "zh", "ja", "ko"),
        { nil: undefined }
      ),
    },
    { requiredKeys: ["title", "confidence"] }
  );

/**
 * Generates a mixed-script string (non-Latin + Latin/spaces) simulating
 * real-world multilingual input like Hinglish.
 */
const arbMixedScriptString = (minLen: number, maxLen: number): fc.Arbitrary<string> =>
  fc
    .array(
      fc.oneof(
        fc.constantFrom(...DEVANAGARI_CHARS),
        fc.constantFrom(...CJK_CHARS),
        fc.constantFrom(" ", "a", "b", "c", "1", "2", "3")
      ),
      { minLength: minLen, maxLength: maxLen }
    )
    .map((chars) => chars.join(""));

// ─── Helper: detect non-Latin characters in string ─────────────────────────────

/**
 * Returns true if the string contains at least one character from a non-Latin script.
 */
function containsNonLatin(str: string): boolean {
  // Matches Devanagari, Arabic, CJK, Hiragana, Katakana, Hangul
  return /[\u0900-\u097F\u0600-\u06FF\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF]/.test(str);
}

/**
 * Returns all non-Latin characters from a string in order.
 */
function extractNonLatinChars(str: string): string {
  return str.replace(/[^\u0900-\u097F\u0600-\u06FF\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF]/g, "");
}

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 6: Language and script preservation", () => {
  describe("Validator accepts non-Latin script titles", () => {
    it("Devanagari (Hindi) titles are accepted by the validator", () => {
      fc.assert(
        fc.property(arbNonLatinParseResponse(arbDevanagariString(1, 50)), (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("Arabic titles are accepted by the validator", () => {
      fc.assert(
        fc.property(arbNonLatinParseResponse(arbArabicString(1, 50)), (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("CJK (Chinese) titles are accepted by the validator", () => {
      fc.assert(
        fc.property(arbNonLatinParseResponse(arbCJKString(1, 50)), (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("Japanese Hiragana titles are accepted by the validator", () => {
      fc.assert(
        fc.property(arbNonLatinParseResponse(arbHiraganaString(1, 50)), (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("Korean Hangul titles are accepted by the validator", () => {
      fc.assert(
        fc.property(arbNonLatinParseResponse(arbKoreanString(1, 50)), (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Title preserves original script characters exactly", () => {
    it("non-Latin title characters are preserved without transliteration in validated output", () => {
      fc.assert(
        fc.property(
          arbNonLatinParseResponse(arbNonLatinString(1, 50)),
          (response) => {
            const cleaned = JSON.parse(JSON.stringify(response));
            const result = validateParseResponse(cleaned);

            expect(result.valid).toBe(true);
            if (!result.valid) return;

            // The validated output title must be identical to the input title
            expect(result.data.title).toBe(cleaned.title);

            // The title must still contain non-Latin characters (not transliterated to Latin)
            expect(containsNonLatin(result.data.title)).toBe(true);
          }
        ),
        { numRuns: 300 }
      );
    });

    it("every non-Latin character in the input title appears in the output title", () => {
      fc.assert(
        fc.property(
          arbNonLatinParseResponse(arbNonLatinString(2, 100)),
          (response) => {
            const cleaned = JSON.parse(JSON.stringify(response));
            const result = validateParseResponse(cleaned);

            expect(result.valid).toBe(true);
            if (!result.valid) return;

            const inputNonLatin = extractNonLatinChars(cleaned.title);
            const outputNonLatin = extractNonLatinChars(result.data.title);

            // All non-Latin characters must be preserved in order
            expect(outputNonLatin).toBe(inputNonLatin);
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("Description preserves original script characters exactly", () => {
    it("non-Latin description characters are preserved without transliteration", () => {
      const arbWithDescription = fc.record({
        title: arbNonLatinString(1, 50),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        description: arbNonLatinString(1, 200),
      });

      fc.assert(
        fc.property(arbWithDescription, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (!result.valid) return;

          // Description must be preserved exactly
          expect(result.data.description).toBe(cleaned.description);

          // Must still contain non-Latin characters
          expect(containsNonLatin(result.data.description!)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Mixed-script content is preserved", () => {
    it("mixed Devanagari + Latin strings preserve all characters", () => {
      fc.assert(
        fc.property(
          arbNonLatinParseResponse(arbMixedScriptString(2, 50)),
          (response) => {
            const cleaned = JSON.parse(JSON.stringify(response));
            const result = validateParseResponse(cleaned);

            expect(result.valid).toBe(true);
            if (!result.valid) return;

            // Exact character preservation
            expect(result.data.title).toBe(cleaned.title);
          }
        ),
        { numRuns: 200 }
      );
    });

    it("titles at maximum length (200 chars) with non-Latin scripts are accepted", () => {
      fc.assert(
        fc.property(
          arbNonLatinParseResponse(arbNonLatinString(200, 200)),
          (response) => {
            const cleaned = JSON.parse(JSON.stringify(response));
            const result = validateParseResponse(cleaned);

            expect(result.valid).toBe(true);
            if (!result.valid) return;

            expect(result.data.title).toBe(cleaned.title);
            expect(result.data.title.length).toBe(200);
          }
        ),
        { numRuns: 50 }
      );
    });
  });

  describe("Non-Latin tags are preserved", () => {
    it("tags containing non-Latin characters are accepted and preserved", () => {
      const arbWithNonLatinTags = fc.record({
        title: arbNonLatinString(1, 50),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        tags: fc.array(arbNonLatinString(1, 50), { minLength: 1, maxLength: 10 }),
      });

      fc.assert(
        fc.property(arbWithNonLatinTags, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (!result.valid) return;

          // Each tag must be preserved exactly
          expect(result.data.tags).toEqual(cleaned.tags);

          // Each tag must still contain non-Latin characters
          for (const tag of result.data.tags!) {
            expect(containsNonLatin(tag)).toBe(true);
          }
        }),
        { numRuns: 200 }
      );
    });
  });
});
