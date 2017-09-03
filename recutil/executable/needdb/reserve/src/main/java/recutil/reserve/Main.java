/*
 * Copyright (C) 2017 normal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package recutil.reserve;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import recutil.commandexecutor.CommandExecutor;
import recutil.commandexecutor.CommandResult;
import recutil.commandexecutor.DummyExecutor;
import recutil.commandexecutor.Executor;
import static recutil.commmonutil.Util.parseDateToString;
import recutil.dbaccessor.entity.Programme;
import recutil.dbaccessor.manager.EntityManagerMaker;
import recutil.loggerconfigurator.LoggerConfigurator;
import static recutil.reserve.Main.RESERVE_COMMAND_PARAMS.OPTION_FILE;
import static recutil.reserve.Main.RESERVE_COMMAND_PARAMS.OPTION_TIME;
import static recutil.reserve.Main.RESERVE_COMMAND_PARAMS.RESERVE_COMMAND;

/**
 * チャンネルIDと番組IDに該当する番組を予約する。
 *
 * @author normal
 */
public class Main {

    private static final Logger LOG = LoggerConfigurator.getCallerLogger();

    protected static final MessageFormat AT_FILE_HEADER = new MessageFormat("タイトル = {0} ,開始時刻 = {1} ,終了時刻 = {2}");

    protected final class RESERVE_COMMAND_PARAMS {

        private RESERVE_COMMAND_PARAMS() {
        }

        public static final String RESERVE_COMMAND = "at";
        public static final String OPTION_TIME = "-t";
        public static final String OPTION_FILE = "-f";
    }

    protected static final MessageFormat RECORD_COMMAND = new MessageFormat("executerecordcommand -i {0} -s {1}");

    protected static final String DATETIME_FORMAT = recutil.commmonutil.Util.getDbDatePattern();

    protected static final String getSep() {
        return System.getProperty("line.separator");
    }

    private static String dumpArgs(String[] args) {
        return ArrayUtils.toString(args, "引数なし。");
    }

    public static void main(String[] args) {
        try {
            if (true) {
                new Main().start(new Executor(), args);
            } else {
                new Main().start(new DummyExecutor(), args);
            }
            System.exit(0);
        } catch (Throwable ex) {
            LOG.error("エラー。 引数 = " + dumpArgs(args), ex);
            System.exit(1);
        }
    }

    protected void start(final CommandExecutor executor, final String[] args) throws ParseException, InterruptedException {

        LOG.debug(dumpArgs(args));

        final Option ChannelIdOption = Option.builder("i")
                .longOpt("channelid")
                .required(true)
                .desc("チャンネルIDオプション。番組のチャンネルIDを指定する。")
                .hasArg(true)
                .type(String.class)
                .build();

        final Option eventIdOption = Option.builder("v")
                .longOpt("eventid")
                .required(true)
                .desc("番組IDオプション。番組IDを指定する。")
                .hasArg()
                .type(Integer.class)
                .build();

        final Option startDateTimeOption = Option.builder("s")
                .longOpt("startdatetime")
                .required(true)
                .desc("放送開始日時オプション。放送開始日時を指定する。" + getSep()
                        + "形式 = " + DATETIME_FORMAT + getSep())
                .hasArg()
                .type(Date.class)
                .build();
        Options opts = new Options();
        opts.addOption(ChannelIdOption);
        opts.addOption(eventIdOption);
        opts.addOption(startDateTimeOption);
        CommandLineParser parser = new DefaultParser();

        HelpFormatter help = new HelpFormatter();

        final CommandLine cl;
        try {
            cl = parser.parse(opts, args);
        } catch (org.apache.commons.cli.ParseException ex) {
            help.printHelp("指定された番組を予約する。", opts);
            LOG.warn("解釈不能なオプション。 {}", ReflectionToStringBuilder.toString(args), ex);
            throw ex;
        }
        final String channelId;
        if (cl.hasOption(ChannelIdOption.getOpt())) {
            channelId = cl.getOptionValue(ChannelIdOption.getOpt());
        } else {
            channelId = null;
            throw new IllegalArgumentException("チャンネルIDが取得できませんでした。。");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("チャンネルID = {}", channelId);
        }

        final Integer eventId;
        if (cl.hasOption(eventIdOption.getOpt())) {
            eventId = Integer.valueOf(cl.getOptionValue(eventIdOption.getOpt()));
        } else {
            eventId = null;
            throw new IllegalArgumentException("番組IDが取得できませんでした。");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("番組ID = {}", Integer.toString(eventId));
        }

        final Date startDateTime;
        if (cl.hasOption(startDateTimeOption.getOpt())) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);
            try {
                startDateTime = sdf.parse(cl.getOptionValue(startDateTimeOption.getOpt()));
            } catch (java.text.ParseException ex) {
                final String s = "放送開始日時を取得できませんでした。";
                throw new IllegalArgumentException(s, ex);
            }
        } else {
            startDateTime = null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("放送開始時刻 = {}", parseDateToString(startDateTime));
        }

        final Programme p;
        try (EntityManagerMaker mk = new EntityManagerMaker()) {
            EntityManager man = mk.getEntityManager();
            final TypedQuery<Programme> ql;
            ql = man.createNamedQuery("Programme.findByChannelIdAndEventIdAndStartDatetime", Programme.class);

            ql.setParameter("channelId", channelId);
            ql.setParameter("eventId", eventId);
            ql.setParameter("startDatetime", startDateTime);
            //制約上、複数件該当することはない。
            Programme _p;
            try {
                _p = ql.getSingleResult();
            } catch (javax.persistence.NoResultException ex) {
                System.out.println("番組が見つかりませんでした。");
                _p = null;
            }
            p = _p;
            man.close();
        }

        if (p == null) {
            return;
        }

        //自動削除されるテンポラリファイルを作成。
        final File tempFile;
        try {
            tempFile = File.createTempFile("myApp", ".tmp");
            tempFile.deleteOnExit();

            try ( //テンポラリファイルに録画コマンドを書き込む。
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));) {
                Object[] header_parameters = {p.getTitle(), parseDateToString(p.getStartDatetime()), parseDateToString(p.getStopDatetime())};
                String s0 = AT_FILE_HEADER.format(header_parameters);
                LOG.debug(s0);
                bw.write(s0);
                bw.newLine();

                //放送時間に120秒足す。
                long duration = this.getAirtimeBySecond(p) + 120;
                Object[] command_parameters = {p.getChannelId().getChannelId(), duration};
                String s1 = RECORD_COMMAND.format(command_parameters);
                LOG.debug(s1);
                bw.write(s1);
                bw.newLine();
            }

            //放送開始1分前
            Date t2 = new Date(p.getStartDatetime().getTime() - 60000);
            String s2 = new SimpleDateFormat("yyyyMMddHHmm").format(t2);
            CommandResult res = executor.execCommand(RESERVE_COMMAND, OPTION_TIME, s2, OPTION_FILE, tempFile.getAbsolutePath());
            if (res != null) {
                System.out.println("予約が行われました。");
                LOG.info(res.toString());
                //何故かatコマンドの出力はエラー出力のほうに出てくるので、そっちを表示する。
                System.out.println(res.getStandardError());
                if (res.getReturnCode() > 0) {
                    throw new IllegalArgumentException("予約に失敗した可能性があります。");
                }
            } else {
                throw new IllegalArgumentException("予約コマンドの実行中に何らかの問題が発生しました。予約の成功は保証されません。");
            }
        } catch (IOException ex) {
            LOG.error("予約コマンドの生成に失敗しました。", ex);
        }

    }

    /**
     * 完全な情報がそろっているとき、番組の放送時間を秒数で取得する。
     *
     * @param p 番組情報
     * @return 放送時間の秒数。
     * @throws ReserveExecutor.IllegalArgumentException
     * 番組情報の、放送時間フィールドのうち、いずれかがnull。もしくは録画時間が負の数。
     */
    private synchronized long getAirtimeBySecond(Programme p) throws IllegalArgumentException {
        Long at = 0L;
        StringBuilder buf = new StringBuilder();
        final String CRLF = getSep();

        if (!((p.getStartDatetime() == null) || (p.getStopDatetime() == null))) {
            at = (p.getStopDatetime().getTime() - p.getStartDatetime().getTime()) / 1000;
        } else {
            buf.append("番組情報の、放送時間フィールドのうち、いずれかがnullです。");
            buf.append(CRLF);
            buf.append(p.toString());
            throw new IllegalArgumentException(buf.toString());
        }
        if (0 > at) {
            buf.append("録画時間に異常があります。録画時間(秒)=");
            buf.append(at);
            buf.append(CRLF);
            buf.append(p.toString());
            throw new IllegalArgumentException(buf.toString());
        }
        return at;
    }
}