package com.github.rwitzel.streamflyer.support;

import static org.junit.Assert.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.github.rwitzel.streamflyer.core.AfterModification;
import com.github.rwitzel.streamflyer.core.Modifier;
import com.github.rwitzel.streamflyer.core.ModifyingReader;
import com.github.rwitzel.streamflyer.regex.MatchProcessor;
import com.github.rwitzel.streamflyer.regex.MatchProcessorResult;
import com.github.rwitzel.streamflyer.regex.OnStreamMatcher;
import com.github.rwitzel.streamflyer.regex.RegexModifier;
import com.github.rwitzel.streamflyer.regex.addons.util.DoNothingProcessor;
import com.github.rwitzel.streamflyer.util.ModificationFactory;
import com.github.rwitzel.streamflyer.util.statistics.LineColumnAwareModificationFactory;

/**
 * <a href="https://groups.google.com/forum/#!topic/streamflyer-discuss/tfoOlLFqLLc">Tracking the line and column
 * number</a>
 * 
 * @author rwoo
 */
public class LineColumnOnRegexMatchTest {

    private LineColumnAwareModificationFactory fac;

    private int firstCharIndex;

    public class PositionAwareRegexModifier extends RegexModifier {

        public PositionAwareRegexModifier(String regex, int flags, MatchProcessor matchProcessor,
                int minimumLengthOfLookBehind, int newNumberOfChars) {
            super(regex, flags, matchProcessor, minimumLengthOfLookBehind, newNumberOfChars);
        }

        @Override
        public AfterModification modify(StringBuilder characterBuffer, int firstModifiableCharacterInBuffer,
                boolean endOfStreamHit) {

            firstCharIndex = firstModifiableCharacterInBuffer;

            return super.modify(characterBuffer, firstModifiableCharacterInBuffer, endOfStreamHit);
        }

        @Override
        protected void init(OnStreamMatcher matcher, MatchProcessor matchProcessor, int minimumLengthOfLookBehind,
                int newNumberOfChars) {
            super.init(matcher, matchProcessor, minimumLengthOfLookBehind, newNumberOfChars);

            ModificationFactory delegate = new ModificationFactory(minimumLengthOfLookBehind, newNumberOfChars);
            this.factory = new LineColumnAwareModificationFactory(delegate);
            this.matchProcessor = matchProcessor;
            this.matcher = matcher;
            this.newNumberOfChars = newNumberOfChars;

            fac = (LineColumnAwareModificationFactory) this.factory;
        }

    }

    public class PositionSavingMatchProcessor extends DoNothingProcessor {

        private List<String> matchPositions = new ArrayList<String>();

        @Override
        public MatchProcessorResult process(StringBuilder characterBuffer, int firstModifiableCharacterInBuffer,
                MatchResult matchResult) {

            String unmatched = characterBuffer.substring(firstCharIndex, matchResult.start());
            String[] lines = unmatched.split("\r\n|\r|\n");

            long lineNumber = fac.getCurrentLine() + lines.length - 1;
            int columnNumber;
            if (lines.length == 1) {
                columnNumber = (int) fac.getCurrentColumn() + matchResult.start() - firstCharIndex;
            } else {
                columnNumber = lines[lines.length - 1].length();
            }

            matchPositions.add("line: " + lineNumber + ", column: " + columnNumber);

            return super.process(characterBuffer, firstModifiableCharacterInBuffer, matchResult);
        }

        public List<String> getMatchPositions() {
            return matchPositions;
        }
    }

    @Test
    public void testFoundLineAndColumn() throws Exception {

        PositionSavingMatchProcessor processor = new PositionSavingMatchProcessor();
        Modifier modifier = new PositionAwareRegexModifier("STREAMFLYER", 0, processor, 1, 500);

        String input = IOUtils.toString(getClass().getResourceAsStream("LineColumnOnRegexMatchTest.txt"), "UTF-8");

        Reader reader = new ModifyingReader(new StringReader(input), modifier);
        IOUtils.toString(reader);
        reader.close();

        List<String> positions = processor.getMatchPositions();
        assertEquals(4, positions.size());
        assertEquals("line: 162, column: 6", positions.get(0));
        assertEquals("line: 162, column: 24", positions.get(1));
        assertEquals("line: 781, column: 6", positions.get(2));
        assertEquals("line: 781, column: 24", positions.get(3));
    }

}
