import time
import bs4
import requests
import urllib.error
from urllib.request import urlopen as uReq
from bs4 import BeautifulSoup as soup

# For the purposes of creating a CSV file for the prize winnings in the CA Daily Three Lotto
#
#
#

draws_searched = []

# Although I could get the draw number relatively easily from the main "Past Winning Numbers" page, to keep the data congruent, I opted to scrape it from the subpages
def get_prize_info(url):
    prize_data = ''
    # Opening the connection and retrieving html page
    try:
        uClient = uReq(url, timeout=120)
        page_html = uClient.read()
        # Closing the connection
        uClient.close()
        page_soup = soup(page_html, "html.parser")
        print('Reading...')
        draw_numbers = page_soup.findAll("h3", {"class":"date"}) # gets the draw number
        draw_text = draw_numbers[0].text.strip()
        first_draw = draw_text[len(draw_numbers[0])-6:]
        prize_data += first_draw + ','
        winning_combos = page_soup.findAll("ul", {"class":"winning_number_sm"}) # The winning combos
        winning_numbers = winning_combos[0].findAll('li')
        first_combo = winning_numbers[0].text + winning_numbers[1].text + winning_numbers[2].text
        prize_data += first_combo + ','
        prizes_info = page_soup.findAll("tbody", {"class":"light-border"}) # The prize names and amounts
        prize_info = prizes_info[0].findAll('td')
        prize_names = prize_info[0].text, prize_info[3].text, prize_info[6].text, prize_info[9].text # Otherwise known as "Straight", "Box", "Straight and Box", "Box Only"
        prize_amounts = prize_info[2].text, prize_info[5].text, prize_info[8].text, prize_info[11].text # The respective prize amounts for the corresponding prize names
        for i in range(4):
            prize_data += prize_names[i] + ',' + prize_amounts[i].replace('$', '') + ','
            draw_text = draw_numbers[1].text.strip()
            sec_draw = draw_text[len(draw_numbers[1])-6:]
            prize_data += sec_draw + ','
            winning_numbers = winning_combos[1].findAll('li')
            sec_combo = winning_numbers[0].text + winning_numbers[1].text + winning_numbers[2].text
            prize_data += sec_combo + ','
            prize_info = prizes_info[1].findAll('td')
            prize_amounts = prize_info[2].text, prize_info[5].text, prize_info[8].text, prize_info[11].text # The respective prize amounts for the corresponding prize names
        for i in range(4):
            prize_data += prize_names[i] + ',' + prize_amounts[i].replace('$', '') + ','
        print('Data successfully captured!')
        return prize_data
    except urllib.error.HTTPError:
        print('File not found')
        return ''
    except requests.exceptions.Timeout:
        print('Timeout occurred with link:', url, 'from within get_prize_data, so one of the links to the draws')
        return ''
    except:
        print('No link made')
        return ''

def go_back(url):
    print('Going back...')
    try:
        uClient = uReq(url, timeout=120)
        page_html = uClient.read()
        uClient.close()
        page_soup = soup(page_html, 'html.parser')
        prev_time = page_soup.findAll("tr", {"class":"d"})
        if prev_time[0].td.a != None:
            new_url = prev_time[0].td.a['href']
            print('About to sleep zzz...')
            time.sleep(10)
            return go_back(new_url)
        else:
            return url
    except requests.exceptions.Timeout:
        print('Timeout occurred with link:', url, 'while going back')
        return ''

def parse_pages(url):
    print('New Capture')
    data = ''
    time.sleep(12)
    try:
        uClient = uReq(url, timeout=120)
        page_html = uClient.read()
        uClient.close()
        page_soup = soup(page_html, 'html.parser')
        links_table = page_soup.findAll("table", {"class":"tag_even numbers"})
        links = links_table[0].findAll("a")
        i = 0
        while i < 20 and links[i] != None: # The number of combinations displayed on one page
            print(i)
            if not links[i].span['title'] in draws_searched: # This link has already been searched
                link = 'https://web.archive.org' + links[i]['href']
                try:
                    request = requests.get(link, timeout=120)
                    if request.history:
                        print('Redirected...')
                        link = request.url
                    else:
                        print('No redirection')
                    new_data = get_prize_info(link)
                    time.sleep(5) # To not exceed the max requests-per-minute limit set by the Internet Archive
                    if new_data != '':
                        draws_searched.append(links[i].span['title'])
                        data += new_data
                except requests.exceptions.Timeout:
                    print('Timeout occurred with link:', link, 'in parse_pages while accessing individual pages')                    
            i += 2 # To skip over repeat of pages
            next_time = page_soup.findAll('tr', {'class': 'd'})[0].findAll('td', {'class': 'f'})
        if next_time[0].a != None:
            new_url = next_time[0].a['href']
            return data + parse_pages(new_url)
        else:
            return data
    except requests.exceptions.Timeout:
        print('Timeout occurred with link:', url, 'while in parse_pages')
        return ''

def parse_all_pages(base_url):
    print('Starting to parse page', base_url[-2:])
    data = ''
    try:
        starting_url = go_back(base_url)
        print('Page is all the way back!')
        print('Beginning parsing...')
        data += parse_pages(starting_url)
        uClient = uReq(starting_url, timeout=120)
        page_html = uClient.read()
        uClient.close()
        page_soup = soup(page_html, 'html.parser')
        pagination_list = page_soup.findAll('ul', {'class':'pagination_actions'})[0].findAll('li')
        for page in pagination_list:
            if page.a != None and page.a.text == 'Next':
                print('New Page!')
                new_url = 'https://web.archive.org' + page.a['href']
                print('Pg.', new_url[-2])
                num_captures = 0
                return data + parse_all_pages(new_url)
        return data
    except requests.exceptions.Timeout:
        print('Timeout occurred with link:', url, 'while going to a new page')
        return ''
    except Exception as e:
        print(e)
        print(starting_url)
        return ''

    
def write_csv(base_url):
    file_name = "OldHDWinnings.csv" # "HD" = Historical Data
    file_buff = open(file_name, "w") # w is to write the file
    headers = "Draw_Number,Winning_Numbers,Prize_Type,Prize_Amount,Prize_Type,Prize_Amount,Prize_Type,Prize_Amount,Prize_Type,Prize_Amount,"
    file_buff.write(headers)
    data = parse_all_pages(base_url)
    file_buff.write(data)
    file_buff.close()

write_csv('https://web.archive.org/web/20120303122355/http://www.calottery.com/play/draw-games/daily-3/winning-numbers/?page=1')
#write_csv('https://web.archive.org/web/20190901030144/https://www.calottery.com/play/draw-games/daily-3/winning-numbers/?page=8')

print("Success!")
